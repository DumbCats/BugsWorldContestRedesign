package edu.osu.bugsworld.engine.sim;

import edu.osu.bugsworld.engine.vm.Action;
import edu.osu.bugsworld.engine.vm.BugProgram;
import edu.osu.bugsworld.engine.vm.CellState;
import edu.osu.bugsworld.engine.vm.Step;
import edu.osu.bugsworld.engine.vm.Vm;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.SplittableRandom;
import java.util.TreeMap;

/**
 * One BugsWorld game. Not thread-safe: one thread owns a game and publishes
 * {@link Snapshot}s to everyone else.
 *
 * <p>Rules (docs/reference-behavior.md §4–5, CONFIRMED against the jar):
 * <ul>
 *   <li>A joining species gets {@code bugsPerSpecies} bugs on random empty
 *       cells, each with a random facing and PC 0.</li>
 *   <li>Each tick shuffles all bugs into a fresh random order. In that order
 *       each bug looks at the cell ahead <em>as it is now</em>, runs its
 *       program to its next action, and the action happens immediately.</li>
 *   <li>{@code move} only into an empty cell; {@code infect} only an enemy,
 *       which switches species, program and resets its PC to 0, keeping its
 *       position and facing.</li>
 *   <li>Cells outside the grid are walls.</li>
 * </ul>
 *
 * <p>Everything random (placement, facing, turn order, the {@code random}
 * condition) comes from one generator seeded by {@link GameConfig#seed()},
 * used in a fixed order, so a game replays exactly from its seed, settings
 * and the order species were added.
 */
public final class Game {

    public enum Phase {
        /** Species can be added or removed. */
        SETUP,
        /** At least one tick has run. Pausing is up to the caller, who stops calling tick(). */
        RUNNING,
        ENDED
    }

    public enum EndReason {
        MANUAL,
        TICK_LIMIT,
        ONE_SPECIES_LEFT
    }

    /** Where to put one bug; used by tests to build exact scenarios. */
    record Placement(int row, int column, Facing facing) {
    }

    private final GameConfig config;
    private final SplittableRandom rng;
    private final Bug[][] grid;
    private final List<Bug> bugs = new ArrayList<>();
    private final TreeMap<Integer, Species> species = new TreeMap<>();
    private final TreeMap<Integer, int[]> minMax = new TreeMap<>();
    private final TreeMap<Integer, int[]> lostTurns = new TreeMap<>();
    private List<Event> lastEvents = List.of();
    private int nextBugId;
    private long tick;
    private Phase phase = Phase.SETUP;
    private EndReason endReason;

    public Game(GameConfig config) {
        this.config = config;
        this.rng = new SplittableRandom(config.seed());
        this.grid = new Bug[config.rows()][config.columns()];
    }

    public GameConfig config() {
        return config;
    }

    public Phase phase() {
        return phase;
    }

    /** Ticks completed so far. */
    public long ticks() {
        return tick;
    }

    /**
     * Adds a species and places its bugs on random empty cells.
     *
     * @throws IllegalStateException if the game has started, already has
     *                               {@value GameConfig#MAX_SPECIES} species, or
     *                               the world has too few empty cells
     */
    public Species addSpecies(String name, BugProgram program) {
        int free = config.rows() * config.columns() - bugs.size();
        if (free < config.bugsPerSpecies()) {
            throw new IllegalStateException("Not enough room for " + name + ": the world has "
                    + free + " empty cells but each species needs " + config.bugsPerSpecies());
        }
        Species s = newSpecies(name, program);
        List<int[]> cells = new ArrayList<>();
        for (int r = 0; r < config.rows(); r++) {
            for (int c = 0; c < config.columns(); c++) {
                if (grid[r][c] == null) {
                    cells.add(new int[] {r, c});
                }
            }
        }
        shuffle(cells);
        for (int i = 0; i < config.bugsPerSpecies(); i++) {
            int[] cell = cells.get(i);
            place(s, cell[0], cell[1], Facing.values()[rng.nextInt(4)]);
        }
        startStats(s);
        return s;
    }

    /** Adds a species with bugs at exact positions (for tests). */
    Species addSpecies(String name, BugProgram program, List<Placement> placements) {
        Species s = newSpecies(name, program);
        for (Placement p : placements) {
            if (grid[p.row()][p.column()] != null) {
                throw new IllegalArgumentException("Cell " + p + " is taken");
            }
            place(s, p.row(), p.column(), p.facing());
        }
        startStats(s);
        return s;
    }

    /** Removes a species and its bugs before the game starts. */
    public void removeSpecies(int id) {
        requireSetup();
        if (species.remove(id) == null) {
            throw new IllegalArgumentException("No species " + id);
        }
        for (Bug b : bugs) {
            if (b.species.id() == id) {
                grid[b.row][b.column] = null;
            }
        }
        bugs.removeIf(b -> b.species.id() == id);
        minMax.remove(id);
        lostTurns.remove(id);
    }

    public List<Species> species() {
        return List.copyOf(species.values());
    }

    /**
     * Runs one tick and returns the resulting snapshot. The first tick moves
     * the game from SETUP to RUNNING.
     *
     * @throws IllegalStateException if the game has ended or has no species
     */
    public Snapshot step() {
        if (phase == Phase.ENDED) {
            throw new IllegalStateException("The game has ended");
        }
        if (species.isEmpty()) {
            throw new IllegalStateException("Add at least one species first");
        }
        phase = Phase.RUNNING;
        tick++;
        List<Event> events = new ArrayList<>();
        lostTurns.replaceAll((id, counts) -> new int[3]);
        Set<Bug> infectedThisTick = config.infectedBugsActSameTick() ? null : new HashSet<>();

        shuffle(bugs);
        for (Bug b : bugs) {
            if (infectedThisTick != null && infectedThisTick.contains(b)) {
                continue;
            }
            int aheadRow = b.row + b.facing.rowDelta();
            int aheadColumn = b.column + b.facing.columnDelta();
            Bug ahead = inside(aheadRow, aheadColumn) ? grid[aheadRow][aheadColumn] : null;
            CellState sees = !inside(aheadRow, aheadColumn) ? CellState.WALL
                    : ahead == null ? CellState.EMPTY
                    : ahead.species == b.species ? CellState.FRIEND
                    : CellState.ENEMY;

            Step s = Vm.step(b.species.program(), b.pc, sees, rng, config.jumpBudget());
            b.pc = s.nextPc();
            act(b, s.action(), sees, ahead, aheadRow, aheadColumn, events, infectedThisTick);
        }
        lastEvents = List.copyOf(events);
        updateStats();
        checkEnd();
        return snapshot();
    }

    /** Ends the game now. */
    public void end() {
        if (phase != Phase.ENDED) {
            phase = Phase.ENDED;
            endReason = EndReason.MANUAL;
        }
    }

    public Snapshot snapshot() {
        List<BugView> views = new ArrayList<>(bugs.size());
        for (Bug b : bugs) {
            views.add(b.view());
        }
        views.sort(Comparator.comparingInt(BugView::id));
        int[] counts = counts();
        List<SpeciesStats> stats = new ArrayList<>();
        for (Species s : species.values()) {
            int[] mm = minMax.get(s.id());
            int[] lost = lostTurns.get(s.id());
            stats.add(new SpeciesStats(s.id(), s.name(), counts[s.id()], mm[0], mm[1], lost[0],
                    lost[1], lost[2]));
        }
        return new Snapshot(tick, phase, endReason, config.rows(), config.columns(),
                List.copyOf(views), List.copyOf(stats), lastEvents);
    }

    private void act(Bug b, Action action, CellState sees, Bug ahead, int aheadRow,
            int aheadColumn, List<Event> events, Set<Bug> infectedThisTick) {
        switch (action) {
            case MOVE -> {
                if (sees == CellState.EMPTY) {
                    grid[b.row][b.column] = null;
                    b.row = aheadRow;
                    b.column = aheadColumn;
                    grid[b.row][b.column] = b;
                    events.add(new Event.Moved(b.id, b.row, b.column));
                }
            }
            case TURN_LEFT -> {
                b.facing = b.facing.left();
                events.add(new Event.Turned(b.id, b.facing));
            }
            case TURN_RIGHT -> {
                b.facing = b.facing.right();
                events.add(new Event.Turned(b.id, b.facing));
            }
            case INFECT -> {
                if (sees == CellState.ENEMY) {
                    int from = ahead.species.id();
                    ahead.species = b.species;
                    ahead.pc = 0;
                    if (infectedThisTick != null) {
                        infectedThisTick.add(ahead);
                    }
                    events.add(new Event.Infected(b.id, ahead.id, from, b.species.id()));
                }
            }
            case SKIP -> {
                // nothing happens
            }
            case STUCK -> lostTurns.get(b.species.id())[0]++;
            case OUT_OF_JUMPS -> lostTurns.get(b.species.id())[1]++;
            case HALTED -> lostTurns.get(b.species.id())[2]++;
        }
    }

    private Species newSpecies(String name, BugProgram program) {
        requireSetup();
        if (species.size() >= GameConfig.MAX_SPECIES) {
            throw new IllegalStateException(
                    "A game can have at most " + GameConfig.MAX_SPECIES + " species");
        }
        int id = 0;
        while (species.containsKey(id)) {
            id++;
        }
        Species s = new Species(id, name, program);
        species.put(id, s);
        return s;
    }

    private void place(Species s, int row, int column, Facing facing) {
        Bug b = new Bug(nextBugId++, s, row, column, facing);
        grid[row][column] = b;
        bugs.add(b);
    }

    private void startStats(Species s) {
        int count = counts()[s.id()];
        minMax.put(s.id(), new int[] {count, count});
        lostTurns.put(s.id(), new int[3]);
    }

    private void updateStats() {
        int[] counts = counts();
        minMax.forEach((id, mm) -> {
            mm[0] = Math.min(mm[0], counts[id]);
            mm[1] = Math.max(mm[1], counts[id]);
        });
    }

    private void checkEnd() {
        if (config.maxTicks() > 0 && tick >= config.maxTicks()) {
            phase = Phase.ENDED;
            endReason = EndReason.TICK_LIMIT;
        } else if (config.endWhenOneSpeciesLeft() && species.size() > 1) {
            int alive = 0;
            for (int count : counts()) {
                if (count > 0) {
                    alive++;
                }
            }
            if (alive <= 1) {
                phase = Phase.ENDED;
                endReason = EndReason.ONE_SPECIES_LEFT;
            }
        }
    }

    private int[] counts() {
        int[] counts = new int[GameConfig.MAX_SPECIES];
        for (Bug b : bugs) {
            counts[b.species.id()]++;
        }
        return counts;
    }

    private boolean inside(int row, int column) {
        return row >= 0 && row < config.rows() && column >= 0 && column < config.columns();
    }

    private void requireSetup() {
        if (phase != Phase.SETUP) {
            throw new IllegalStateException("Species can only change before the game starts");
        }
    }

    /** Fisher-Yates with the game's generator (never Collections.shuffle's own). */
    private <T> void shuffle(List<T> list) {
        for (int i = list.size() - 1; i > 0; i--) {
            Collections.swap(list, i, rng.nextInt(i + 1));
        }
    }
}
