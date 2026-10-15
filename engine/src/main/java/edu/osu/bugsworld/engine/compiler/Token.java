package edu.osu.bugsworld.engine.compiler;

/** A BL token: a maximal run of non-whitespace characters. */
public record Token(Kind kind, String text, Span span) {

    public enum Kind {
        KEYWORD,
        CONDITION,
        /** Matches {@code [a-zA-Z][a-zA-Z0-9-]*} and is not a keyword or condition. Includes primitives. */
        IDENTIFIER,
        /** Any other run of characters, e.g. {@code go_now} or {@code #}. */
        INVALID,
        EOF
    }

    /** True if this is the keyword {@code keyword}. */
    public boolean is(String keyword) {
        return kind == Kind.KEYWORD && text.equals(keyword);
    }

    /** How to show this token in a message. */
    public String describe() {
        return kind == Kind.EOF ? "the end of the file" : "\"" + text + "\"";
    }
}
