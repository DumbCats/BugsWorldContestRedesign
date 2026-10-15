package edu.osu.bugsworld.engine.vm;

import edu.osu.bugsworld.engine.ObjectCode;
import edu.osu.bugsworld.engine.Opcode;
import java.util.Arrays;

/**
 * Object code prepared for execution. Immutable, so one instance is shared by
 * every bug of a species.
 *
 * <p>For each {@link CellState} it precomputes which addresses can still
 * reach a primitive or HALT. During one turn what the bug sees cannot change,
 * so the only freedom is the {@code random} condition. If no choice of random
 * outcomes leads to an action, the bug is certainly stuck, and the VM can say
 * so at once instead of looping.
 */
public final class BugProgram {

    private final ObjectCode objectCode;
    private final int[] code;
    /** terminates[sees][pc]; index code.length means "ran off the end". */
    private final boolean[][] terminates = new boolean[CellState.values().length][];

    private BugProgram(ObjectCode objectCode) {
        this.objectCode = objectCode;
        this.code = objectCode.toArray();
        for (CellState sees : CellState.values()) {
            terminates[sees.ordinal()] = computeTerminates(sees);
        }
    }

    public static BugProgram of(ObjectCode code) {
        return new BugProgram(code);
    }

    public ObjectCode objectCode() {
        return objectCode;
    }

    int length() {
        return code.length;
    }

    int at(int pc) {
        return code[pc];
    }

    /** Can a bug at {@code pc} that sees {@code sees} reach an action this turn? */
    boolean terminates(CellState sees, int pc) {
        return terminates[sees.ordinal()][pc];
    }

    /**
     * Whether the condition tested by conditional jump {@code opcode} holds:
     * 1 for true, 0 for false, -1 if it is {@code random}.
     */
    static int condition(int opcode, CellState sees) {
        boolean holds;
        switch (Opcode.fromCode(opcode)) {
            case JUMP_IF_NOT_NEXT_IS_EMPTY -> holds = sees == CellState.EMPTY;
            case JUMP_IF_NOT_NEXT_IS_NOT_EMPTY -> holds = sees != CellState.EMPTY;
            case JUMP_IF_NOT_NEXT_IS_WALL -> holds = sees == CellState.WALL;
            case JUMP_IF_NOT_NEXT_IS_NOT_WALL -> holds = sees != CellState.WALL;
            case JUMP_IF_NOT_NEXT_IS_FRIEND -> holds = sees == CellState.FRIEND;
            case JUMP_IF_NOT_NEXT_IS_NOT_FRIEND -> holds = sees != CellState.FRIEND;
            case JUMP_IF_NOT_NEXT_IS_ENEMY -> holds = sees == CellState.ENEMY;
            case JUMP_IF_NOT_NEXT_IS_NOT_ENEMY -> holds = sees != CellState.ENEMY;
            case JUMP_IF_NOT_TRUE -> holds = true;
            case JUMP_IF_NOT_RANDOM -> {
                return -1;
            }
            default -> throw new IllegalArgumentException("Not a conditional jump: " + opcode);
        }
        return holds ? 1 : 0;
    }

    /** Reverse breadth-first search from the addresses that end a turn. */
    private boolean[] computeTerminates(CellState sees) {
        int n = code.length;
        // Successor edges of every instruction (at most two), as pred lists.
        int[] predCount = new int[n + 2];
        forEachEdge(sees, (from, to) -> predCount[to + 1]++);
        for (int i = 1; i < predCount.length; i++) {
            predCount[i] += predCount[i - 1];
        }
        int[] preds = new int[predCount[n + 1]];
        int[] fill = Arrays.copyOf(predCount, n + 1);
        forEachEdge(sees, (from, to) -> preds[fill[to]++] = from);

        boolean[] result = new boolean[n + 1];
        int[] queue = new int[n + 1];
        int head = 0;
        int tail = 0;
        result[n] = true;
        queue[tail++] = n;
        for (int pc = 0; pc < n; pc = next(pc)) {
            if (Opcode.fromCode(code[pc]).isPrimitive()) {
                result[pc] = true;
                queue[tail++] = pc;
            }
        }
        while (head < tail) {
            int node = queue[head++];
            for (int i = predCount[node]; i < predCount[node + 1]; i++) {
                int p = preds[i];
                if (!result[p]) {
                    result[p] = true;
                    queue[tail++] = p;
                }
            }
        }
        return result;
    }

    private interface EdgeVisitor {
        void edge(int from, int to);
    }

    private void forEachEdge(CellState sees, EdgeVisitor visitor) {
        for (int pc = 0; pc < code.length; pc = next(pc)) {
            int op = code[pc];
            if (Opcode.fromCode(op).isPrimitive()) {
                continue;
            }
            if (op == Opcode.JUMP.code()) {
                visitor.edge(pc, code[pc + 1]);
                continue;
            }
            int holds = condition(op, sees);
            if (holds != 0) {
                visitor.edge(pc, pc + 2); // fall through when the condition holds
            }
            if (holds != 1) {
                visitor.edge(pc, code[pc + 1]);
            }
        }
    }

    private int next(int pc) {
        return pc + (Opcode.fromCode(code[pc]).hasOperand() ? 2 : 1);
    }
}
