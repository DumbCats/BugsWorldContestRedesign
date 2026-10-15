package edu.osu.bugsworld.engine.sim;

/**
 * A bug as seen from outside the engine.
 *
 * @param id      stable for the whole game
 * @param species current species id (changes when infected)
 * @param row     0-based playable row
 * @param column  0-based playable column
 * @param facing  direction faced
 * @param pc      where its program continues; map to source with the compiler's source map
 */
public record BugView(int id, int species, int row, int column, Facing facing, int pc) {
}
