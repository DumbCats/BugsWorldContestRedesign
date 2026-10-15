package edu.osu.bugsworld.engine.compiler;

import edu.osu.bugsworld.engine.ObjectCode;
import java.util.List;

/**
 * The outcome of compiling BL source.
 *
 * @param program     the syntax tree; partial after errors, {@code null} for empty input
 * @param code        the object code, or {@code null} if there are errors
 * @param sourceMap   for each address in {@code code}, the span of the statement that
 *                    produced it (inlined calls map into the instruction's body); empty on failure
 * @param diagnostics errors and warnings in source order
 */
public record CompileResult(Ast.Program program, ObjectCode code, List<Span> sourceMap,
        List<Diagnostic> diagnostics) {

    public boolean succeeded() {
        return code != null;
    }

    public List<Diagnostic> errors() {
        return diagnostics.stream().filter(Diagnostic::isError).toList();
    }

    public List<Diagnostic> warnings() {
        return diagnostics.stream().filter(d -> !d.isError()).toList();
    }

    /** The program reformatted in the standard style, or {@code null} if there are errors. */
    public String formatted() {
        return succeeded() ? PrettyPrinter.print(program) : null;
    }
}
