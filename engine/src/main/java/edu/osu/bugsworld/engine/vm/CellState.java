package edu.osu.bugsworld.engine.vm;

/** What a bug sees in the cell it faces. */
public enum CellState {
    EMPTY,
    WALL,
    /** A bug of the same species. */
    FRIEND,
    /** A bug of another species. */
    ENEMY
}
