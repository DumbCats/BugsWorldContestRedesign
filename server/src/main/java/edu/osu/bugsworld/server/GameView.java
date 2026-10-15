package edu.osu.bugsworld.server;

import edu.osu.bugsworld.engine.sim.BugView;
import edu.osu.bugsworld.engine.sim.GameConfig;
import edu.osu.bugsworld.engine.sim.Snapshot;
import edu.osu.bugsworld.engine.sim.SpeciesStats;
import java.util.List;
import java.util.Map;

/**
 * The game as sent to browsers, over both {@code GET /api/game} and the
 * {@code /ws/game} WebSocket.
 *
 * @param gameNumber     increases with every new game or reset, so viewers can tell games apart
 * @param status         {@code setup}, {@code running}, {@code paused} or {@code ended}
 * @param endReason      {@code MANUAL}, {@code TICK_LIMIT}, {@code ONE_SPECIES_LEFT}, or null
 * @param ticksPerSecond current speed
 * @param settings       the game's settings, including its seed
 * @param species        per-species stats plus the submission each came from
 * @param bugs           five numbers per bug, flattened to keep frames small:
 *                       id, species, row, column, facing (0 N, 1 E, 2 S, 3 W)
 */
public record GameView(int gameNumber, String status, String endReason, long tick, int rows,
        int columns, int ticksPerSecond, GameConfig settings, List<SpeciesView> species,
        int[] bugs) {

    public record SpeciesView(int id, String name, Long submissionId, int current, int min,
            int max, int stuck, int outOfJumps, int halted) {
    }

    static GameView of(int gameNumber, String status, Snapshot s, GameConfig settings,
            int ticksPerSecond, Map<Integer, Long> submissionIds) {
        int[] bugs = new int[s.bugs().size() * 5];
        int i = 0;
        for (BugView b : s.bugs()) {
            bugs[i++] = b.id();
            bugs[i++] = b.species();
            bugs[i++] = b.row();
            bugs[i++] = b.column();
            bugs[i++] = b.facing().ordinal();
        }
        List<SpeciesView> species = s.species().stream()
                .map((SpeciesStats st) -> new SpeciesView(st.species(), st.name(),
                        submissionIds.get(st.species()), st.current(), st.min(), st.max(),
                        st.stuck(), st.outOfJumps(), st.halted()))
                .toList();
        return new GameView(gameNumber, status,
                s.endReason() == null ? null : s.endReason().name(), s.tick(), s.rows(),
                s.columns(), ticksPerSecond, settings, species, bugs);
    }

    /** The same view without the bug list, for stored results. */
    GameView withoutBugs() {
        return new GameView(gameNumber, status, endReason, tick, rows, columns, ticksPerSecond,
                settings, species, new int[0]);
    }
}
