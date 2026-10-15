package edu.osu.bugsworld.engine.sim;

import edu.osu.bugsworld.engine.vm.BugProgram;

/**
 * A species in a game: one submitted program.
 *
 * @param id      0 to 9, unique within the game
 * @param name    shown on the display, e.g. the student's or program's name
 * @param program run by every bug of this species
 */
public record Species(int id, String name, BugProgram program) {
}
