package edu.osu.bugsworld.engine.vm;

/**
 * The result of running a bug's program for one turn.
 *
 * @param action what the bug does
 * @param nextPc the program counter to start from next turn
 * @param jumps  jump instructions executed this turn, whether or not they jumped
 */
public record Step(Action action, int nextPc, int jumps) {
}
