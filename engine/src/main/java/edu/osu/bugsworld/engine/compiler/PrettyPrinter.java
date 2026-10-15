package edu.osu.bugsworld.engine.compiler;

import java.util.List;

/**
 * Formats a program in the reference style (4-space indents, a blank line
 * after the header and after each instruction). Instructions keep their
 * source order; the reference prints them in hash-map order.
 */
public final class PrettyPrinter {

    private PrettyPrinter() {
    }

    public static String print(Ast.Program program) {
        StringBuilder out = new StringBuilder();
        out.append("PROGRAM ").append(program.name()).append(" IS\n\n");
        for (Ast.Instruction i : program.instructions()) {
            out.append("    INSTRUCTION ").append(i.name()).append(" IS\n");
            block(out, i.body(), 8);
            out.append("    END ").append(i.name()).append("\n\n");
        }
        out.append("BEGIN\n");
        block(out, program.body(), 4);
        out.append("END ").append(program.name()).append('\n');
        return out.toString();
    }

    private static void block(StringBuilder out, List<Ast.Stmt> block, int indent) {
        for (Ast.Stmt s : block) {
            String pad = " ".repeat(indent);
            switch (s) {
                case Ast.Call c -> out.append(pad).append(c.name()).append('\n');
                case Ast.If i -> {
                    out.append(pad).append("IF ").append(i.condition().text()).append(" THEN\n");
                    block(out, i.thenBlock(), indent + 4);
                    out.append(pad).append("END IF\n");
                }
                case Ast.IfElse i -> {
                    out.append(pad).append("IF ").append(i.condition().text()).append(" THEN\n");
                    block(out, i.thenBlock(), indent + 4);
                    out.append(pad).append("ELSE\n");
                    block(out, i.elseBlock(), indent + 4);
                    out.append(pad).append("END IF\n");
                }
                case Ast.While w -> {
                    out.append(pad).append("WHILE ").append(w.condition().text()).append(" DO\n");
                    block(out, w.body(), indent + 4);
                    out.append(pad).append("END WHILE\n");
                }
            }
        }
    }
}
