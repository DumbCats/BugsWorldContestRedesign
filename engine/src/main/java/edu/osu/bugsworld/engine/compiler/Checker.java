package edu.osu.bugsworld.engine.compiler;

import edu.osu.bugsworld.engine.compiler.Diagnostic.Severity;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.SequencedSet;
import java.util.Set;

/**
 * Checks a parsed program for errors that depend on more than the grammar.
 *
 * <p>CONFIRMED reference behavior this matches: instruction names may not be
 * primitives or duplicates; calls may refer to instructions defined later;
 * undefined calls and recursion are errors <em>only on paths reachable from
 * BEGIN</em>, because the reference finds them while inlining from the main
 * body. In an instruction that is never called they are warnings, so every
 * program the reference accepts is still accepted.
 */
final class Checker {

    private final Ast.Program program;
    private final Diagnostics diags;
    private final Map<String, Ast.Instruction> table = new LinkedHashMap<>();
    private final Set<String> analyzed = new HashSet<>();
    private final SequencedSet<String> path = new LinkedHashSet<>();

    private Checker(Ast.Program program, Diagnostics diags) {
        this.program = program;
        this.diags = diags;
    }

    /**
     * Checks {@code program} and returns its instruction table (name to
     * first definition).
     */
    static Map<String, Ast.Instruction> check(Ast.Program program, Diagnostics diags) {
        Checker c = new Checker(program, diags);
        c.buildTable();
        c.checkCalls();
        return c.table;
    }

    private void buildTable() {
        for (Ast.Instruction i : program.instructions()) {
            if (i.name() == null || Lexer.classify(i.name()) != Token.Kind.IDENTIFIER) {
                continue; // the parser already reported the bad name
            }
            if (Primitive.fromName(i.name()).isPresent()) {
                diags.error("primitive-name", i.nameSpan(),
                        i.name() + " is a primitive instruction, so it can't be redefined",
                        "Pick another name");
                continue;
            }
            Ast.Instruction first = table.putIfAbsent(i.name(), i);
            if (first != null) {
                diags.error("duplicate-instruction", i.nameSpan(),
                        "Instruction " + i.name() + " is already defined at line "
                                + first.nameSpan().line(),
                        "Rename one of them");
            }
        }
    }

    private void checkCalls() {
        block(program.body(), Severity.ERROR, null);
        Set<String> reachable = Set.copyOf(analyzed);
        List<Ast.Instruction> unused = new ArrayList<>();
        for (Ast.Instruction i : table.values()) {
            if (!reachable.contains(i.name())) {
                unused.add(i);
                diags.warning("unused-instruction", i.nameSpan(),
                        "Instruction " + i.name() + " is never called", null);
            }
        }
        for (Ast.Instruction i : unused) {
            call(i.name(), i.nameSpan(), Severity.WARNING, i.name());
        }
    }

    /**
     * @param unusedRoot for warnings in never-called code, the unused
     *                   instruction we came from; {@code null} for the main body
     */
    private void block(List<Ast.Stmt> block, Severity severity, String unusedRoot) {
        for (Ast.Stmt s : block) {
            switch (s) {
                case Ast.Call c -> {
                    if (Primitive.fromName(c.name()).isEmpty()) {
                        call(c.name(), c.span(), severity, unusedRoot);
                    }
                }
                case Ast.If i -> block(i.thenBlock(), severity, unusedRoot);
                case Ast.IfElse i -> {
                    block(i.thenBlock(), severity, unusedRoot);
                    block(i.elseBlock(), severity, unusedRoot);
                }
                case Ast.While w -> block(w.body(), severity, unusedRoot);
            }
        }
    }

    private void call(String name, Span span, Severity severity, String unusedRoot) {
        String note = unusedRoot == null ? ""
                : " (only a problem once " + unusedRoot + " is called)";
        Ast.Instruction target = table.get(name);
        if (target == null) {
            report(severity, "undefined-instruction", span,
                    "Instruction " + name + " is not defined" + note, undefinedHint(name));
        } else if (path.contains(name)) {
            List<String> cycle = new ArrayList<>(path);
            cycle = cycle.subList(cycle.indexOf(name), cycle.size());
            report(severity, "recursion", span,
                    "Instruction " + name + " calls itself (" + String.join(" -> ", cycle) + " -> "
                            + name + ")" + note,
                    "BL doesn't allow recursion; use WHILE to repeat things");
        } else if (analyzed.add(name)) {
            path.add(name);
            block(target.body(), severity, unusedRoot);
            path.remove(name);
        }
    }

    private String undefinedHint(String name) {
        String upper = name.toUpperCase(Locale.ROOT);
        if (Lexer.KEYWORDS.contains(upper)) {
            return "Keywords must be in capital letters: " + upper;
        }
        List<String> candidates = new ArrayList<>(Primitive.NAMES);
        candidates.addAll(table.keySet());
        return Names.closest(name, candidates)
                .map(s -> "Did you mean " + s + "?" + (s.equalsIgnoreCase(name)
                        ? " Names are case-sensitive." : ""))
                .orElse("Define it with INSTRUCTION " + name + " IS ... END " + name
                        + " before BEGIN");
    }

    private void report(Severity severity, String code, Span span, String message, String hint) {
        if (severity == Severity.ERROR) {
            diags.error(code, span, message, hint);
        } else {
            diags.warning(code, span, message, hint);
        }
    }
}
