package edu.osu.bugsworld.engine.compiler;

import edu.osu.bugsworld.engine.compiler.Token.Kind;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;

/**
 * Recursive-descent parser for BL.
 *
 * <p>On valid input it builds the same tree as the reference
 * {@code Program1.parse} (CONFIRMED grammar). On invalid input it reports an
 * error and recovers instead of stopping, so one compile shows every
 * problem. A stack of open constructs lets it tell "this IF was never closed"
 * from "END WHILE was a typo for END IF".
 */
final class Parser {

    private enum Frame {
        PROGRAM, INSTRUCTION, IF_THEN, IF_ELSE, WHILE
    }

    private record Open(Frame frame, Token opener) {
    }

    private final List<Token> tokens;
    private final Diagnostics diags;
    private final Deque<Open> open = new ArrayDeque<>();
    /** Instructions found after BEGIN; kept so their calls still resolve. */
    private final List<Ast.Instruction> misplaced = new ArrayList<>();
    /** Name of the program or instruction being parsed, which END may close. */
    private String enclosingName;
    private int pos;

    private Parser(List<Token> tokens, Diagnostics diags) {
        this.tokens = tokens;
        this.diags = diags;
    }

    /** Parses a whole program; returns {@code null} only for empty input. */
    static Ast.Program parse(List<Token> tokens, Diagnostics diags) {
        return new Parser(tokens, diags).program();
    }

    // ---------------------------------------------------------------- program

    private Ast.Program program() {
        Token first = peek();
        if (first.kind() == Kind.EOF) {
            diags.error("expected-token", first.span(), "The file is empty",
                    "A BL program starts with PROGRAM name IS");
            return null;
        }
        if (first.is("PROGRAM")) {
            advance();
        } else {
            diags.error("expected-token", first.span(),
                    "Expected PROGRAM at the start of the file, but found " + first.describe(),
                    "A BL program starts with PROGRAM name IS");
        }
        open.push(new Open(Frame.PROGRAM, first));
        Token nameToken = name("program");
        String name = nameToken == null ? null : nameToken.text();
        enclosingName = name;
        expect("IS", "after the program name", first);

        List<Ast.Instruction> instructions = new ArrayList<>();
        while (peek().is("INSTRUCTION")) {
            instructions.add(instruction());
        }
        if (peek().is("BEGIN")) {
            advance();
        } else {
            diags.error("expected-token", peek().span(),
                    "Expected BEGIN or INSTRUCTION, but found " + peek().describe(),
                    "Define any instructions first, then write BEGIN before the main statements");
        }
        enclosingName = name;
        List<Ast.Stmt> body = block();
        Span end = closeNamed(first, "program", name);
        open.pop();

        if (peek().kind() != Kind.EOF) {
            Token extra = peek();
            Span rest = Span.covering(extra.span(), tokens.get(tokens.size() - 2).span());
            diags.error("trailing-input", rest,
                    "Unexpected text after the program's final END",
                    extra.is("INSTRUCTION")
                            ? "Instructions must be defined before BEGIN"
                            : "Nothing may follow END " + (name == null ? "name" : name));
        }
        instructions.addAll(misplaced);
        Span nameSpan = nameToken == null ? first.span() : nameToken.span();
        return new Ast.Program(name, nameSpan, List.copyOf(instructions), body, end);
    }

    private Ast.Instruction instruction() {
        Token instructionToken = advance();
        open.push(new Open(Frame.INSTRUCTION, instructionToken));
        Token nameToken = name("instruction");
        String name = nameToken == null ? null : nameToken.text();
        String outerName = enclosingName;
        enclosingName = name;
        expect("IS", "after the instruction name", instructionToken);
        List<Ast.Stmt> body = block();
        closeNamed(instructionToken, "instruction", name);
        open.pop();
        enclosingName = outerName;
        Span nameSpan = nameToken == null ? instructionToken.span() : nameToken.span();
        return new Ast.Instruction(name, nameSpan, body);
    }

    /** Parses a program or instruction name; returns {@code null} if it is missing. */
    private Token name(String what) {
        Token t = peek();
        switch (t.kind()) {
            case IDENTIFIER -> {
                advance();
                return t;
            }
            case KEYWORD -> {
                if (t.is("IS") || t.is("BEGIN") || t.is("END") || t.is("INSTRUCTION")) {
                    diags.error("expected-token", t.span(),
                            "Expected a name for the " + what + ", but found " + t.describe());
                    return null;
                }
                diags.error("invalid-name", t.span(),
                        t.text() + " is a keyword, so it can't be used as a name",
                        "Pick another name for the " + what);
                advance();
                return t;
            }
            case CONDITION -> {
                diags.error("invalid-name", t.span(),
                        t.text() + " is a condition, so it can't be used as a name",
                        "Pick another name for the " + what);
                advance();
                return t;
            }
            case INVALID -> {
                InvalidName why = InvalidName.explain(t.text());
                diags.error("invalid-name", t.span(),
                        "\"" + t.text() + "\" is not a valid " + what + " name: " + why.reason(),
                        why.hint());
                advance();
                return t;
            }
            default -> {
                diags.error("expected-token", t.span(),
                        "Expected a name for the " + what + ", but found " + t.describe());
                return null;
            }
        }
    }

    /**
     * Parses {@code END name} for a program or instruction.
     *
     * @return the span of {@code END name}, or {@code null} if it is missing
     */
    private Span closeNamed(Token opener, String what, String name) {
        Token t = peek();
        if (t.is("END")) {
            advance();
            Token n = peek();
            if (n.kind() == Kind.IDENTIFIER) {
                advance();
                if (name != null && !n.text().equals(name)) {
                    diags.error("end-name-mismatch", n.span(),
                            "END " + n.text() + " doesn't match the " + what + " name " + name,
                            "Write END " + name);
                }
                return Span.covering(t.span(), n.span());
            }
            if (n.text().equals(name)) {
                // A bad name (keyword, condition, invalid) was already
                // reported where it was defined.
                advance();
                return Span.covering(t.span(), n.span());
            }
            if (n.kind() == Kind.CONDITION || n.kind() == Kind.INVALID) {
                advance();
            }
            diags.error("expected-token", n.span(),
                    "Expected the " + what + " name after END, but found " + n.describe(),
                    name == null ? null : "Write END " + name);
            return t.span();
        }
        String label = name == null ? "This " + what : "The " + what + " " + name;
        diags.error("unclosed", opener.span(),
                label + " is never closed (" + reached(t) + ")",
                "Add END " + (name == null ? "name" : name) + " after its last statement");
        return null;
    }

    // ------------------------------------------------------------- statements

    private List<Ast.Stmt> block() {
        List<Ast.Stmt> block = new ArrayList<>();
        while (true) {
            Token t = peek();
            switch (t.kind()) {
                case EOF -> {
                    return block;
                }
                case IDENTIFIER -> {
                    advance();
                    block.add(new Ast.Call(t.text(), t.span()));
                }
                case CONDITION -> {
                    advance();
                    diags.error("unexpected-token", t.span(),
                            "The condition " + t.text() + " can only be used after IF or WHILE");
                }
                case INVALID -> invalidStatement(t);
                case KEYWORD -> {
                    if (!keywordInBlock(t, block)) {
                        return block;
                    }
                }
            }
        }
    }

    /** Handles a keyword inside a block; returns false if it ends the block. */
    private boolean keywordInBlock(Token t, List<Ast.Stmt> block) {
        switch (t.text()) {
            case "IF" -> block.add(ifStatement());
            case "WHILE" -> block.add(whileStatement());
            case "END" -> {
                Token n = peek(1);
                if ((n.is("IF") || n.is("WHILE")) && !insideStatement()
                        && !n.text().equals(enclosingName)) {
                    advance();
                    advance();
                    diags.error("unexpected-token", Span.covering(t.span(), n.span()),
                            "END " + n.text() + " has no matching " + n.text(),
                            "Remove it, or add the missing " + n.text() + " above it");
                    return true;
                }
                return false;
            }
            case "ELSE" -> {
                if (elseClosesStatement()) {
                    return false;
                }
                advance();
                diags.error("unexpected-token", t.span(), "ELSE without a matching IF ... THEN");
            }
            case "INSTRUCTION" -> {
                if (insideInstruction()) {
                    return false; // the current instruction is missing its END
                }
                diags.error("misplaced-instruction", t.span(),
                        "Instructions must be defined before BEGIN",
                        "Move this INSTRUCTION ... END block above BEGIN");
                misplaced.add(instruction());
            }
            case "BEGIN" -> {
                if (insideInstruction()) {
                    return false;
                }
                advance();
                diags.error("unexpected-token", t.span(), "This program already has a BEGIN");
            }
            default -> {
                advance();
                diags.error("unexpected-token", t.span(),
                        "Unexpected " + t.text() + " here", misplacedKeywordHint(t.text()));
            }
        }
        return true;
    }

    private Ast.Stmt ifStatement() {
        Token ifToken = advance();
        Condition condition = condition(ifToken);
        Span header = Span.covering(ifToken.span(), previous().span());
        expect("THEN", "after the condition", ifToken);
        open.push(new Open(Frame.IF_THEN, ifToken));
        List<Ast.Stmt> thenBlock = block();
        List<Ast.Stmt> elseBlock = null;
        if (peek().is("ELSE")) {
            advance();
            open.pop();
            open.push(new Open(Frame.IF_ELSE, ifToken));
            elseBlock = block();
        }
        close(ifToken, "IF");
        open.pop();
        return elseBlock == null
                ? new Ast.If(condition, thenBlock, header)
                : new Ast.IfElse(condition, thenBlock, elseBlock, header);
    }

    private Ast.Stmt whileStatement() {
        Token whileToken = advance();
        Condition condition = condition(whileToken);
        Span header = Span.covering(whileToken.span(), previous().span());
        expect("DO", "after the condition", whileToken);
        open.push(new Open(Frame.WHILE, whileToken));
        List<Ast.Stmt> body = block();
        close(whileToken, "WHILE");
        open.pop();
        return new Ast.While(condition, body, header);
    }

    /** Parses the condition after IF or WHILE; returns {@code null} on error. */
    private Condition condition(Token opener) {
        Token t = peek();
        if (t.kind() == Kind.CONDITION) {
            advance();
            return Condition.fromText(t.text()).orElseThrow();
        }
        String conditions = "The conditions are " + String.join(", ", Condition.TEXTS);
        if (t.kind() == Kind.KEYWORD || t.kind() == Kind.EOF) {
            diags.error("expected-token", t.span(),
                    "Expected a condition after " + opener.text() + ", but found " + t.describe(),
                    conditions);
            return null;
        }
        Token n = peek(1);
        if ((t.text().equalsIgnoreCase("not") || t.text().equals("!")) && n.kind() == Kind.CONDITION) {
            advance();
            advance();
            Condition c = Condition.fromText(n.text()).orElseThrow();
            diags.error("unknown-condition", Span.covering(t.span(), n.span()),
                    "BL has no " + t.text() + " operator",
                    c.negated().map(x -> "Write " + x.text() + " instead")
                            .orElse("Swap what happens in the THEN and ELSE parts instead"));
            return null;
        }
        advance();
        diags.error("unknown-condition", t.span(), "\"" + t.text() + "\" is not a condition",
                Names.closest(t.text(), Condition.TEXTS).map(s -> "Did you mean " + s + "?")
                        .orElse(conditions));
        return null;
    }

    /** Expects keyword {@code kw}; if it is missing, reports it and carries on as if present. */
    private void expect(String kw, String where, Token opener) {
        Token t = peek();
        if (t.is(kw)) {
            advance();
            return;
        }
        String swapped = kw.equals("THEN") ? "DO" : kw.equals("DO") ? "THEN" : null;
        if (swapped != null && t.is(swapped)) {
            advance();
            diags.error("expected-token", t.span(),
                    opener.text() + " uses " + kw + ", not " + swapped,
                    "Write " + opener.text() + " condition " + kw);
            return;
        }
        if (swapped != null && isConjunction(t.text())) {
            advance();
            Token last = t;
            if (peek().kind() == Kind.CONDITION) {
                last = advance();
            }
            diags.error("unknown-condition", Span.covering(t.span(), last.span()),
                    "BL has no " + t.text() + " operator: each " + opener.text()
                            + " tests exactly one condition",
                    "Put one IF inside another instead");
            if (peek().is(kw)) {
                advance();
            }
            return;
        }
        diags.error("expected-token", t.span(),
                "Expected " + kw + " " + where + ", but found " + t.describe());
    }

    /** Parses {@code END kw} closing the IF or WHILE started by {@code opener}. */
    private void close(Token opener, String kw) {
        Token t = peek();
        String opened = kw + " at line " + opener.span().line();
        if (t.is("END")) {
            Token n = peek(1);
            if (n.is(kw)) {
                advance();
                advance();
                return;
            }
            if (n.is("IF") || n.is("WHILE")) {
                if (outerStatementIs(n.text())) {
                    // The END belongs to an enclosing statement: ours is unclosed.
                    unclosed(opener, kw, "found END " + n.text() + " at line " + n.span().line()
                            + " first");
                    return;
                }
                advance();
                advance();
                diags.error("mismatched-end", Span.covering(t.span(), n.span()),
                        "Expected END " + kw + " to close the " + opened + ", but found END "
                                + n.text(),
                        "Change it to END " + kw);
                return;
            }
            if (n.kind() == Kind.IDENTIFIER) {
                unclosed(opener, kw, "reached END " + n.text() + " at line " + n.span().line());
                return;
            }
            advance();
            diags.error("expected-token", n.span(),
                    "Expected " + kw + " after END to close the " + opened + ", but found "
                            + n.describe());
            return;
        }
        unclosed(opener, kw, reached(t));
    }

    private void unclosed(Token opener, String kw, String context) {
        diags.error("unclosed", opener.span(), "This " + kw + " is never closed (" + context + ")",
                "Add END " + kw + " after its last statement");
    }

    private void invalidStatement(Token t) {
        if (looksLikeComment(t.text())) {
            int line = t.span().line();
            Token last = t;
            while (peek().kind() != Kind.EOF && peek().span().line() == line) {
                last = advance();
            }
            diags.error("comment", Span.covering(t.span(), last.span()), "BL has no comments",
                    "Delete this text");
            return;
        }
        advance();
        InvalidName why = InvalidName.explain(t.text());
        diags.error("invalid-name", t.span(),
                "\"" + t.text() + "\" is not a valid instruction name: " + why.reason(), why.hint());
    }

    // ---------------------------------------------------------------- helpers

    private boolean insideInstruction() {
        return open.stream().anyMatch(o -> o.frame() == Frame.INSTRUCTION);
    }

    /** True if the innermost open construct is an IF or WHILE. */
    private boolean insideStatement() {
        Open top = open.peek();
        return top != null && top.frame() != Frame.PROGRAM && top.frame() != Frame.INSTRUCTION;
    }

    /** True if an ELSE here would belong to an open IF ... THEN. */
    private boolean elseClosesStatement() {
        for (Open o : open) {
            if (o.frame() == Frame.IF_THEN) {
                return true;
            }
            if (o.frame() == Frame.PROGRAM || o.frame() == Frame.INSTRUCTION) {
                return false;
            }
        }
        return false;
    }

    /** True if an IF or WHILE (per {@code kw}) encloses the innermost open statement. */
    private boolean outerStatementIs(String kw) {
        boolean first = true;
        for (Open o : open) {
            if (first) {
                first = false;
                continue;
            }
            switch (o.frame()) {
                case PROGRAM, INSTRUCTION -> {
                    return false;
                }
                case IF_THEN, IF_ELSE -> {
                    if (kw.equals("IF")) {
                        return true;
                    }
                }
                case WHILE -> {
                    if (kw.equals("WHILE")) {
                        return true;
                    }
                }
            }
        }
        return false;
    }

    private static String reached(Token t) {
        return t.kind() == Kind.EOF
                ? "reached the end of the file"
                : "reached " + t.describe() + " at line " + t.span().line();
    }

    private static boolean isConjunction(String text) {
        return text.equalsIgnoreCase("and") || text.equalsIgnoreCase("or")
                || text.equals("&&") || text.equals("||");
    }

    private static boolean looksLikeComment(String text) {
        return text.startsWith("#") || text.startsWith("//") || text.startsWith("/*")
                || text.startsWith("--") || text.startsWith(";") || text.startsWith("%");
    }

    private static String misplacedKeywordHint(String kw) {
        return switch (kw) {
            case "THEN" -> "THEN goes right after the condition of an IF";
            case "DO" -> "DO goes right after the condition of a WHILE";
            case "IS" -> "IS only follows a PROGRAM or INSTRUCTION name";
            case "PROGRAM" -> "A file holds exactly one PROGRAM";
            default -> null;
        };
    }

    private Token peek() {
        return peek(0);
    }

    private Token peek(int ahead) {
        return tokens.get(Math.min(pos + ahead, tokens.size() - 1));
    }

    private Token previous() {
        return tokens.get(Math.max(pos - 1, 0));
    }

    private Token advance() {
        Token t = peek();
        if (t.kind() != Kind.EOF) {
            pos++;
        }
        return t;
    }
}
