package edu.osu.bugsworld.engine;

import java.util.Arrays;

/**
 * A validated, immutable compiled BL program.
 *
 * <p>CONFIRMED layout (reference compiler in lib/): a flat sequence of ints with
 * no header. Primitives take one slot; jumps take two (opcode, absolute target).
 * Calls are inlined at compile time, and the compiler always appends
 * {@link Opcode#HALT}.
 *
 * <p>CONFIRMED {@code .bo} text format (what BWClient loads): the first line
 * is the number of ints, then one int per line.
 */
public final class ObjectCode {

    private final int[] code;

    private ObjectCode(int[] code) {
        this.code = code;
    }

    /**
     * Validates and wraps {@code code}: every slot must decode as an
     * instruction or as the operand of the jump before it, and every jump
     * must target the start of an instruction.
     *
     * <p>OURS: the jar only requires targets to be in range. Jumping into an
     * operand would make the jar run the operand as an opcode (and crash if
     * it is not one); the reference compiler never generates such code.
     *
     * @throws IllegalArgumentException describing the first problem found
     */
    public static ObjectCode of(int... code) {
        if (code.length == 0) {
            throw new IllegalArgumentException("Object code is empty");
        }
        boolean[] isStart = new boolean[code.length];
        int pc = 0;
        while (pc < code.length) {
            if (!Opcode.isValid(code[pc])) {
                throw new IllegalArgumentException(
                        "Unknown instruction code " + code[pc] + " at address " + pc);
            }
            isStart[pc] = true;
            Opcode op = Opcode.fromCode(code[pc]);
            if (op.hasOperand() && pc + 1 >= code.length) {
                throw new IllegalArgumentException(
                        op + " at address " + pc + " is missing its target");
            }
            pc += op.hasOperand() ? 2 : 1;
        }
        for (pc = 0; pc < code.length; pc += Opcode.fromCode(code[pc]).hasOperand() ? 2 : 1) {
            if (Opcode.fromCode(code[pc]).hasOperand()) {
                int target = code[pc + 1];
                if (target < 0 || target >= code.length) {
                    throw new IllegalArgumentException(Opcode.fromCode(code[pc]) + " at address "
                            + pc + " jumps to " + target + ", outside 0.." + (code.length - 1));
                }
                if (!isStart[target]) {
                    throw new IllegalArgumentException(Opcode.fromCode(code[pc]) + " at address "
                            + pc + " jumps to " + target + ", the middle of an instruction");
                }
            }
        }
        return new ObjectCode(code.clone());
    }

    /**
     * Parses the {@code .bo} text format.
     *
     * @throws IllegalArgumentException if the text is malformed or invalid
     */
    public static ObjectCode parseBo(String text) {
        String[] tokens = text.strip().split("\\s+");
        int length = parseInt(tokens[0], "length");
        if (tokens.length - 1 != length) {
            throw new IllegalArgumentException("Header says " + length
                    + " ints but file contains " + (tokens.length - 1));
        }
        int[] code = new int[length];
        for (int i = 0; i < length; i++) {
            code[i] = parseInt(tokens[i + 1], "value at address " + i);
        }
        return of(code);
    }

    private static int parseInt(String token, String what) {
        try {
            return Integer.parseInt(token);
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("Bad " + what + ": \"" + token + "\"", e);
        }
    }

    /** Formats this program in the {@code .bo} text format. */
    public String toBo() {
        StringBuilder sb = new StringBuilder().append(code.length).append('\n');
        for (int x : code) {
            sb.append(x).append('\n');
        }
        return sb.toString();
    }

    public int length() {
        return code.length;
    }

    public int get(int address) {
        return code[address];
    }

    public int[] toArray() {
        return code.clone();
    }

    @Override
    public boolean equals(Object o) {
        return o instanceof ObjectCode other && Arrays.equals(code, other.code);
    }

    @Override
    public int hashCode() {
        return Arrays.hashCode(code);
    }

    @Override
    public String toString() {
        return Arrays.toString(code);
    }
}
