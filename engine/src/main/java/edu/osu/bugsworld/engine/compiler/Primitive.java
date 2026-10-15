package edu.osu.bugsworld.engine.compiler;

import edu.osu.bugsworld.engine.Opcode;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;

/** The five primitive instructions a BL program can call. */
public enum Primitive {
    MOVE("move", Opcode.MOVE),
    TURNLEFT("turnleft", Opcode.TURNLEFT),
    TURNRIGHT("turnright", Opcode.TURNRIGHT),
    INFECT("infect", Opcode.INFECT),
    SKIP("skip", Opcode.SKIP);

    public static final List<String> NAMES = Arrays.stream(values()).map(Primitive::text).toList();

    private final String text;
    private final Opcode opcode;

    Primitive(String text, Opcode opcode) {
        this.text = text;
        this.opcode = opcode;
    }

    public String text() {
        return text;
    }

    public Opcode opcode() {
        return opcode;
    }

    public static Optional<Primitive> fromName(String name) {
        return Arrays.stream(values()).filter(p -> p.text.equals(name)).findFirst();
    }
}
