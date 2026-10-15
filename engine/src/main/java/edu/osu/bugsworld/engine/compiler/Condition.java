package edu.osu.bugsworld.engine.compiler;

import edu.osu.bugsworld.engine.Opcode;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;

/** The ten BL conditions and the jump each compiles to. */
public enum Condition {
    NEXT_IS_EMPTY("next-is-empty", Opcode.JUMP_IF_NOT_NEXT_IS_EMPTY),
    NEXT_IS_NOT_EMPTY("next-is-not-empty", Opcode.JUMP_IF_NOT_NEXT_IS_NOT_EMPTY),
    NEXT_IS_WALL("next-is-wall", Opcode.JUMP_IF_NOT_NEXT_IS_WALL),
    NEXT_IS_NOT_WALL("next-is-not-wall", Opcode.JUMP_IF_NOT_NEXT_IS_NOT_WALL),
    NEXT_IS_FRIEND("next-is-friend", Opcode.JUMP_IF_NOT_NEXT_IS_FRIEND),
    NEXT_IS_NOT_FRIEND("next-is-not-friend", Opcode.JUMP_IF_NOT_NEXT_IS_NOT_FRIEND),
    NEXT_IS_ENEMY("next-is-enemy", Opcode.JUMP_IF_NOT_NEXT_IS_ENEMY),
    NEXT_IS_NOT_ENEMY("next-is-not-enemy", Opcode.JUMP_IF_NOT_NEXT_IS_NOT_ENEMY),
    RANDOM("random", Opcode.JUMP_IF_NOT_RANDOM),
    TRUE("true", Opcode.JUMP_IF_NOT_TRUE);

    public static final List<String> TEXTS = Arrays.stream(values()).map(Condition::text).toList();

    private final String text;
    private final Opcode jumpIfNot;

    Condition(String text, Opcode jumpIfNot) {
        this.text = text;
        this.jumpIfNot = jumpIfNot;
    }

    /** The condition as written in BL. */
    public String text() {
        return text;
    }

    /** The conditional jump taken when this condition is false. */
    public Opcode jumpIfNot() {
        return jumpIfNot;
    }

    public static Optional<Condition> fromText(String text) {
        return Arrays.stream(values()).filter(c -> c.text.equals(text)).findFirst();
    }

    /** {@code next-is-X} becomes {@code next-is-not-X} and vice versa; empty for random and true. */
    public Optional<Condition> negated() {
        if (!text.startsWith("next-is-")) {
            return Optional.empty();
        }
        String negated = text.startsWith("next-is-not-")
                ? text.replace("next-is-not-", "next-is-")
                : text.replace("next-is-", "next-is-not-");
        return fromText(negated);
    }
}
