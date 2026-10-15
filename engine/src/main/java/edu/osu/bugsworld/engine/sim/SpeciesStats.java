package edu.osu.bugsworld.engine.sim;

/**
 * Per-species numbers, like the jar's stats panel plus why bugs lost turns.
 *
 * @param species    species id
 * @param name       display name
 * @param current    bugs alive now
 * @param min        lowest count since the species joined (OURS: includes 0)
 * @param max        highest count since the species joined
 * @param stuck      bugs that were {@code STUCK} in the last tick
 * @param outOfJumps bugs that ran out of jumps in the last tick
 * @param halted     bugs whose program has halted, as of the last tick
 */
public record SpeciesStats(int species, String name, int current, int min, int max, int stuck,
        int outOfJumps, int halted) {
}
