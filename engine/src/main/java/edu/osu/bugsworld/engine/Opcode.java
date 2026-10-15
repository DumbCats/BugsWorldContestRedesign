package edu.osu.bugsworld.engine;

/**
 * BugsWorld VM instruction set.
 *
 * <p>CONFIRMED: codes and names match the OSU {@code Program.Instruction}
 * Javadoc, and the reference compiler and server in lib/ agree with them.
 * Conditional jumps jump to their operand when the condition is <em>false</em>
 * and fall through otherwise; {@link #JUMP} always jumps. Jump targets are
 * absolute addresses.
 */
public enum Opcode {
    MOVE,
    TURNLEFT,
    TURNRIGHT,
    INFECT,
    SKIP,
    HALT,
    JUMP,
    JUMP_IF_NOT_NEXT_IS_EMPTY,
    JUMP_IF_NOT_NEXT_IS_NOT_EMPTY,
    JUMP_IF_NOT_NEXT_IS_WALL,
    JUMP_IF_NOT_NEXT_IS_NOT_WALL,
    JUMP_IF_NOT_NEXT_IS_FRIEND,
    JUMP_IF_NOT_NEXT_IS_NOT_FRIEND,
    JUMP_IF_NOT_NEXT_IS_ENEMY,
    JUMP_IF_NOT_NEXT_IS_NOT_ENEMY,
    JUMP_IF_NOT_RANDOM,
    JUMP_IF_NOT_TRUE;

    private static final Opcode[] BY_CODE = values();

    /** The integer that represents this instruction in object code. */
    public int code() {
        return ordinal();
    }

    /** True for the instructions that end a bug's turn (codes 0 to 5). */
    public boolean isPrimitive() {
        return ordinal() <= HALT.ordinal();
    }

    /** True for instructions followed by a jump-target operand. */
    public boolean hasOperand() {
        return !isPrimitive();
    }

    /** True if {@code code} is a valid instruction code. */
    public static boolean isValid(int code) {
        return code >= 0 && code < BY_CODE.length;
    }

    /**
     * Returns the instruction with the given code.
     *
     * @throws IllegalArgumentException if {@code code} is not valid
     */
    public static Opcode fromCode(int code) {
        if (!isValid(code)) {
            throw new IllegalArgumentException("Unknown instruction code " + code);
        }
        return BY_CODE[code];
    }
}
