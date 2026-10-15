package edu.osu.bugsworld.engine.compiler;

/**
 * A range of source text. {@code offset} and {@code length} are in chars;
 * {@code line} and {@code column} (both 1-based) locate the first char.
 */
public record Span(int offset, int length, int line, int column) {

    /** The smallest span that covers both {@code first} and {@code last}. */
    public static Span covering(Span first, Span last) {
        return new Span(first.offset, last.end() - first.offset, first.line, first.column);
    }

    /** Offset just past the last char. */
    public int end() {
        return offset + length;
    }

    @Override
    public String toString() {
        return line + ":" + column;
    }
}
