package edu.osu.bugsworld.engine.compiler;

/**
 * A compiler error or warning.
 *
 * @param severity whether this stops compilation
 * @param code     stable, machine-readable id (e.g. {@code "undefined-instruction"})
 * @param message  what is wrong, in plain words
 * @param span     where in the source
 * @param hint     how to fix it, or {@code null}
 */
public record Diagnostic(Severity severity, String code, String message, Span span, String hint) {

    public enum Severity {
        ERROR,
        WARNING
    }

    public boolean isError() {
        return severity == Severity.ERROR;
    }

    /** Formats as {@code line:column: error: message (hint)}. */
    @Override
    public String toString() {
        String text = span + ": " + severity.name().toLowerCase() + ": " + message;
        return hint == null ? text : text + " (" + hint + ")";
    }
}
