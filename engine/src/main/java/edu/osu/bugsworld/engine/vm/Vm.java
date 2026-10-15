package edu.osu.bugsworld.engine.vm;

import edu.osu.bugsworld.engine.Opcode;
import java.util.random.RandomGenerator;

/**
 * The BugsWorld virtual machine: runs one bug's program until it chooses
 * its action for this turn.
 *
 * <p>CONFIRMED semantics (jar {@code Bug.getNextPrimitive}, and checked
 * against it by VmReferenceTest): starting at the bug's PC, follow jumps
 * until a primitive, return it, and continue after it next turn. A
 * conditional jump jumps when its condition is false and otherwise skips its
 * operand. {@code random} is a fair coin; we draw exactly one boolean from
 * {@code rng} per evaluation, so a seeded game replays exactly.
 *
 * <p>OURS (docs/reference-behavior.md §10): a HALTed bug stays halted
 * instead of crashing the game, and a loop that cannot act returns
 * {@link Action#STUCK} instead of freezing it.
 */
public final class Vm {

    /**
     * Jumps allowed per turn. Only loops whose exit depends on {@code random}
     * can reach it, because certain infinite loops are caught as STUCK first.
     */
    public static final int DEFAULT_JUMP_BUDGET = 10_000;

    private Vm() {
    }

    public static Step step(BugProgram program, int pc, CellState sees, RandomGenerator rng) {
        return step(program, pc, sees, rng, DEFAULT_JUMP_BUDGET);
    }

    public static Step step(BugProgram program, int pc, CellState sees, RandomGenerator rng,
            int jumpBudget) {
        int jumps = 0;
        while (true) {
            if (pc >= program.length()) {
                return new Step(Action.HALTED, pc, jumps);
            }
            if (!program.terminates(sees, pc)) {
                return new Step(Action.STUCK, pc, jumps);
            }
            int op = program.at(pc);
            if (op < Opcode.HALT.code()) {
                return new Step(Action.ofPrimitive(op), pc + 1, jumps);
            }
            if (op == Opcode.HALT.code()) {
                return new Step(Action.HALTED, pc, jumps);
            }
            if (jumps == jumpBudget) {
                return new Step(Action.OUT_OF_JUMPS, pc, jumps);
            }
            jumps++;
            if (op == Opcode.JUMP.code()) {
                pc = program.at(pc + 1);
            } else {
                int holds = BugProgram.condition(op, sees);
                boolean taken = holds == -1 ? rng.nextBoolean() : holds == 1;
                pc = taken ? pc + 2 : program.at(pc + 1);
            }
        }
    }
}
