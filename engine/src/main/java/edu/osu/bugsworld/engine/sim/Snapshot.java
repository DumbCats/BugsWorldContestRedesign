package edu.osu.bugsworld.engine.sim;

import java.util.List;

/**
 * An immutable picture of a game, safe to share with other threads.
 *
 * @param tick      ticks completed
 * @param phase     lifecycle phase
 * @param endReason why the game ended, or {@code null} while it hasn't
 * @param rows      playable rows
 * @param columns   playable columns
 * @param bugs      every bug, ordered by id
 * @param species   per-species stats, ordered by species id
 * @param events    what changed in the last tick (empty before the first)
 */
public record Snapshot(long tick, Game.Phase phase, Game.EndReason endReason, int rows,
        int columns, List<BugView> bugs, List<SpeciesStats> species, List<Event> events) {
}
