package edu.osu.bugsworld.engine.compiler;

import edu.osu.bugsworld.engine.Opcode;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

/**
 * Generates object code from an error-free program.
 *
 * <p>CONFIRMED patterns (reference {@code ProgramSecondary.generatedCode}),
 * where {@code JIN_c} is the jump-if-not opcode of condition {@code c}:
 * <pre>
 *   IF c THEN s END IF            JIN_c L; s; L:
 *   IF c THEN s ELSE t END IF     JIN_c E; s; JUMP L; E: t; L:
 *   WHILE c DO s END WHILE        T: JIN_c L; s; JUMP T; L:
 *   call of instruction i         body of i, inlined
 *   program                       body; HALT
 * </pre>
 */
final class CodeGen {

    /** Object code plus, for each address, the statement that produced it. */
    record Output(int[] code, List<Span> sourceMap) {
    }

    private final Map<String, Ast.Instruction> table;
    private int[] code = new int[64];
    private Span[] spans = new Span[64];
    private int length;

    private CodeGen(Map<String, Ast.Instruction> table) {
        this.table = table;
    }

    static Output generate(Ast.Program program, Map<String, Ast.Instruction> table) {
        CodeGen g = new CodeGen(table);
        g.block(program.body());
        g.emit(Opcode.HALT.code(),
                program.endSpan() != null ? program.endSpan() : program.nameSpan());
        return new Output(Arrays.copyOf(g.code, g.length),
                new ArrayList<>(Arrays.asList(g.spans).subList(0, g.length)));
    }

    private void block(List<Ast.Stmt> block) {
        for (Ast.Stmt s : block) {
            statement(s);
        }
    }

    private void statement(Ast.Stmt s) {
        switch (s) {
            case Ast.Call c -> {
                var primitive = Primitive.fromName(c.name());
                if (primitive.isPresent()) {
                    emit(primitive.get().opcode().code(), c.span());
                } else {
                    block(table.get(c.name()).body());
                }
            }
            case Ast.If i -> {
                int patch = jump(i.condition().jumpIfNot(), i.span());
                block(i.thenBlock());
                code[patch] = length;
            }
            case Ast.IfElse i -> {
                int toElse = jump(i.condition().jumpIfNot(), i.span());
                block(i.thenBlock());
                int toEnd = jump(Opcode.JUMP, i.span());
                code[toElse] = length;
                block(i.elseBlock());
                code[toEnd] = length;
            }
            case Ast.While w -> {
                int top = length;
                int patch = jump(w.condition().jumpIfNot(), w.span());
                block(w.body());
                // Not code[jump(...)] = top: jump() may replace the array, and
                // Java evaluates the array reference before the index.
                int back = jump(Opcode.JUMP, w.span());
                code[back] = top;
                code[patch] = length;
            }
        }
    }

    /** Emits a jump with a placeholder target; returns the target's address. */
    private int jump(Opcode op, Span span) {
        emit(op.code(), span);
        emit(0, span);
        return length - 1;
    }

    private void emit(int value, Span span) {
        if (length == code.length) {
            code = Arrays.copyOf(code, length * 2);
            spans = Arrays.copyOf(spans, length * 2);
        }
        code[length] = value;
        spans[length] = span;
        length++;
    }
}
