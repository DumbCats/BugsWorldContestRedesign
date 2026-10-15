package edu.osu.bugsworld.engine.compiler;

import java.util.List;

/**
 * BL abstract syntax tree. After a failed parse, names may be {@code null}
 * and conditions may be {@code null}; code is generated only from error-free
 * trees.
 */
public final class Ast {

    private Ast() {
    }

    /**
     * @param endSpan span of the closing {@code END name}, or {@code null} if missing
     */
    public record Program(String name, Span nameSpan, List<Instruction> instructions,
            List<Stmt> body, Span endSpan) {
    }

    public record Instruction(String name, Span nameSpan, List<Stmt> body) {
    }

    /** A statement. Its span covers the header (e.g. {@code IF next-is-empty}). */
    public sealed interface Stmt permits Call, If, IfElse, While {
        Span span();
    }

    /** A call to a primitive or to a user-defined instruction. */
    public record Call(String name, Span span) implements Stmt {
    }

    public record If(Condition condition, List<Stmt> thenBlock, Span span) implements Stmt {
    }

    public record IfElse(Condition condition, List<Stmt> thenBlock, List<Stmt> elseBlock,
            Span span) implements Stmt {
    }

    public record While(Condition condition, List<Stmt> body, Span span) implements Stmt {
    }
}
