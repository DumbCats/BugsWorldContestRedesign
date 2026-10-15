package edu.osu.bugsworld.engine.sim;

import edu.osu.bugsworld.engine.vm.Vm;

/**
 * Settings for one game. Defaults and ranges match the jar server
 * (docs/reference-behavior.md §3); options marked OURS are off by default.
 * Each field is checked on its own, so the {@code with} methods can be
 * chained in any order; whether the bugs fit is checked as species join.
 *
 * @param rows                    playable rows (walls surround the grid)
 * @param columns                 playable columns
 * @param bugsPerSpecies          bugs placed when a species joins
 * @param seed                    seeds the game's only random number generator
 * @param jumpBudget              VM jumps allowed per bug per turn (OURS; the jar had no limit)
 * @param infectedBugsActSameTick a bug infected before its turn still takes that
 *                                turn as its new species (jar behavior: true)
 * @param maxTicks                end the game after this many ticks; 0 for no limit (OURS)
 * @param endWhenOneSpeciesLeft   end the game when at most one species has bugs (OURS)
 */
public record GameConfig(int rows, int columns, int bugsPerSpecies, long seed, int jumpBudget,
        boolean infectedBugsActSameTick, long maxTicks, boolean endWhenOneSpeciesLeft) {

    public static final int MAX_SPECIES = 10;
    public static final int MIN_SIZE = 1;
    public static final int MAX_SIZE = 100;
    public static final int MIN_BUGS = 1;
    public static final int MAX_BUGS = 300;

    public GameConfig {
        check(rows >= MIN_SIZE && rows <= MAX_SIZE, "rows must be 1 to 100");
        check(columns >= MIN_SIZE && columns <= MAX_SIZE, "columns must be 1 to 100");
        check(bugsPerSpecies >= MIN_BUGS && bugsPerSpecies <= MAX_BUGS,
                "bugs per species must be 1 to 300");
        check(jumpBudget >= 1, "jump budget must be at least 1");
        check(maxTicks >= 0, "tick limit can't be negative");
    }

    private static void check(boolean ok, String message) {
        if (!ok) {
            throw new IllegalArgumentException(message);
        }
    }

    /** The jar's defaults: 15 x 15, 50 bugs per species, no automatic end. */
    public static GameConfig defaults(long seed) {
        return new GameConfig(15, 15, 50, seed, Vm.DEFAULT_JUMP_BUDGET, true, 0, false);
    }

    public GameConfig withSize(int newRows, int newColumns) {
        return new GameConfig(newRows, newColumns, bugsPerSpecies, seed, jumpBudget,
                infectedBugsActSameTick, maxTicks, endWhenOneSpeciesLeft);
    }

    public GameConfig withBugsPerSpecies(int n) {
        return new GameConfig(rows, columns, n, seed, jumpBudget, infectedBugsActSameTick,
                maxTicks, endWhenOneSpeciesLeft);
    }

    public GameConfig withSeed(long newSeed) {
        return new GameConfig(rows, columns, bugsPerSpecies, newSeed, jumpBudget,
                infectedBugsActSameTick, maxTicks, endWhenOneSpeciesLeft);
    }

    public GameConfig withJumpBudget(int budget) {
        return new GameConfig(rows, columns, bugsPerSpecies, seed, budget,
                infectedBugsActSameTick, maxTicks, endWhenOneSpeciesLeft);
    }

    public GameConfig withInfectedBugsActSameTick(boolean value) {
        return new GameConfig(rows, columns, bugsPerSpecies, seed, jumpBudget, value, maxTicks,
                endWhenOneSpeciesLeft);
    }

    public GameConfig withMaxTicks(long ticks) {
        return new GameConfig(rows, columns, bugsPerSpecies, seed, jumpBudget,
                infectedBugsActSameTick, ticks, endWhenOneSpeciesLeft);
    }

    public GameConfig withEndWhenOneSpeciesLeft(boolean value) {
        return new GameConfig(rows, columns, bugsPerSpecies, seed, jumpBudget,
                infectedBugsActSameTick, maxTicks, value);
    }
}
