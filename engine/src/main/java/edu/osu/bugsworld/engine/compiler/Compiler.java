package edu.osu.bugsworld.engine.compiler;

import edu.osu.bugsworld.engine.ObjectCode;
import java.util.List;
import java.util.Map;

/**
 * Compiles BL source to object code: lex, parse, check, generate.
 *
 * <p>Accepts exactly the programs the OSU reference compiler accepts and
 * produces identical object code for them (checked against golden files and
 * the reference jar). The two deliberate differences are a leading
 * byte-order mark, which is ignored, and a cap on object-code size, because
 * inlining can make code grow exponentially.
 */
public final class Compiler {

    /** Upper bound on generated object code length (OURS; the reference has none). */
    public static final int DEFAULT_MAX_CODE_LENGTH = 1_000_000;

    private Compiler() {
    }

    public static CompileResult compile(String source) {
        return compile(source, DEFAULT_MAX_CODE_LENGTH);
    }

    public static CompileResult compile(String source, int maxCodeLength) {
        List<Token> tokens = Lexer.tokenize(source);
        Attempt strict = attempt(tokens, List.of(), maxCodeLength);
        if (!strict.diags.hasErrors()) {
            CodeGen.Output out = CodeGen.generate(strict.program, strict.table);
            return new CompileResult(strict.program, ObjectCode.of(out.code()),
                    List.copyOf(out.sourceMap()), strict.diags.sorted());
        }
        // The program is wrong anyway, so retry treating miscased keywords
        // ("end if") as keywords to get fewer, clearer errors.
        Lexer.Folded folded = Lexer.foldCase(tokens);
        Attempt best = folded.diagnostics().isEmpty()
                ? strict
                : attempt(folded.tokens(), folded.diagnostics(), maxCodeLength);
        return new CompileResult(best.program, null, List.of(), best.diags.sorted());
    }

    private record Attempt(Ast.Program program, Map<String, Ast.Instruction> table,
            Diagnostics diags) {
    }

    private static Attempt attempt(List<Token> tokens, List<Diagnostic> initial, int maxCodeLength) {
        Diagnostics diags = new Diagnostics();
        initial.forEach(diags::add);
        Ast.Program program = Parser.parse(tokens, diags);
        if (program == null) {
            return new Attempt(null, Map.of(), diags);
        }
        Map<String, Ast.Instruction> table = Checker.check(program, diags);
        if (!diags.hasErrors()) {
            Analysis.run(program, table, diags, maxCodeLength);
        }
        return new Attempt(program, table, diags);
    }
}
