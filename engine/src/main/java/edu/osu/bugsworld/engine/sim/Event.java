package edu.osu.bugsworld.engine.sim;

/** A change to the world during a tick, in the order it happened. */
public sealed interface Event {

    int bug();

    record Moved(int bug, int row, int column) implements Event {
    }

    record Turned(int bug, Facing facing) implements Event {
    }

    /** {@code bug} infected {@code victim}, which changed from {@code fromSpecies} to {@code toSpecies}. */
    record Infected(int bug, int victim, int fromSpecies, int toSpecies) implements Event {
    }
}
