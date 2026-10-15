package edu.osu.bugsworld.engine.vm;

/** What a bug does with its turn, as decided by {@link Vm#step}. */
public enum Action {
    MOVE,
    TURN_LEFT,
    TURN_RIGHT,
    INFECT,
    SKIP,
    /** The program has reached HALT (or run off its end) and does nothing from now on. */
    HALTED,
    /**
     * No action is reachable with what the bug currently sees: it is in a
     * loop that never acts. It tries again next turn, when the world may
     * have changed. (The jar server froze the whole game here.)
     */
    STUCK,
    /**
     * The per-turn jump budget ran out in a loop that depends on
     * {@code random}. The bug resumes from where it stopped next turn.
     */
    OUT_OF_JUMPS;

    private static final Action[] PRIMITIVES = {MOVE, TURN_LEFT, TURN_RIGHT, INFECT, SKIP};

    /** The action for primitive opcodes 0 to 4. */
    static Action ofPrimitive(int opcode) {
        return PRIMITIVES[opcode];
    }

    /** True for the five primitive actions a program chooses. */
    public boolean isPrimitive() {
        return ordinal() <= SKIP.ordinal();
    }
}
