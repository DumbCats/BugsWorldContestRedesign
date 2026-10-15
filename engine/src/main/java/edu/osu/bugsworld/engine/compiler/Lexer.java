package edu.osu.bugsworld.engine.compiler;

import edu.osu.bugsworld.engine.compiler.Token.Kind;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Splits BL source into tokens.
 *
 * <p>CONFIRMED (reference {@code components.utilities.Tokenizer}): a token is
 * a maximal run of characters other than space, tab, CR and LF. There is no
 * punctuation and no comment syntax. Classification follows
 * {@code Tokenizer.isKeyword/isCondition/isIdentifier}.
 *
 * <p>OURS: a leading byte-order mark is ignored (the reference would reject it).
 */
public final class Lexer {

    public static final Set<String> KEYWORDS = Set.of(
            "PROGRAM", "IS", "INSTRUCTION", "END", "IF", "THEN", "ELSE", "WHILE", "BEGIN", "DO");

    private static final Set<String> CONDITIONS = Set.copyOf(Condition.TEXTS);
    private static final Pattern IDENTIFIER = Pattern.compile("[a-zA-Z][a-zA-Z0-9-]*");
    private static final String SEPARATORS = " \t\n\r";

    private Lexer() {
    }

    /** Tokenizes {@code source}. The result always ends with an EOF token. */
    public static List<Token> tokenize(String source) {
        List<Token> tokens = new ArrayList<>();
        int i = source.startsWith("﻿") ? 1 : 0;
        int line = 1;
        int column = 1;
        int n = source.length();
        while (i < n) {
            char c = source.charAt(i);
            if (SEPARATORS.indexOf(c) >= 0) {
                if (c == '\n' || (c == '\r' && (i + 1 >= n || source.charAt(i + 1) != '\n'))) {
                    line++;
                    column = 1;
                } else if (c != '\r') {
                    column++;
                }
                i++;
                continue;
            }
            int start = i;
            while (i < n && SEPARATORS.indexOf(source.charAt(i)) < 0) {
                i++;
            }
            String text = source.substring(start, i);
            tokens.add(new Token(classify(text), text, new Span(start, i - start, line, column)));
            column += i - start;
        }
        tokens.add(new Token(Kind.EOF, "", new Span(n, 0, line, column)));
        return tokens;
    }

    static Kind classify(String text) {
        if (KEYWORDS.contains(text)) {
            return Kind.KEYWORD;
        }
        if (CONDITIONS.contains(text)) {
            return Kind.CONDITION;
        }
        return IDENTIFIER.matcher(text).matches() ? Kind.IDENTIFIER : Kind.INVALID;
    }

    /** Result of {@link #foldCase}. */
    record Folded(List<Token> tokens, List<Diagnostic> diagnostics) {
    }

    /**
     * Error-recovery helper: reinterprets identifiers that are keywords or
     * conditions in the wrong case ({@code end}, {@code Then},
     * {@code True}) as the real keyword or condition, with one error each.
     * Names the program defines after PROGRAM or INSTRUCTION are left alone,
     * since {@code end} is a legal instruction name.
     *
     * <p>Only used after a strict parse has already failed, so it never
     * changes which programs are accepted.
     */
    static Folded foldCase(List<Token> tokens) {
        Set<String> definedNames = new HashSet<>();
        for (int i = 0; i + 1 < tokens.size(); i++) {
            Token t = tokens.get(i);
            Token next = tokens.get(i + 1);
            if ((t.is("PROGRAM") || t.is("INSTRUCTION")) && next.kind() == Kind.IDENTIFIER) {
                definedNames.add(next.text());
            }
        }
        List<Token> folded = new ArrayList<>(tokens.size());
        List<Diagnostic> diagnostics = new ArrayList<>();
        for (Token t : tokens) {
            String upper = t.text().toUpperCase(Locale.ROOT);
            String lower = t.text().toLowerCase(Locale.ROOT);
            if (t.kind() != Kind.IDENTIFIER || definedNames.contains(t.text())) {
                folded.add(t);
            } else if (KEYWORDS.contains(upper)) {
                folded.add(new Token(Kind.KEYWORD, upper, t.span()));
                diagnostics.add(new Diagnostic(Diagnostic.Severity.ERROR, "keyword-case",
                        "Keywords must be in capital letters: write " + upper + ", not " + t.text(),
                        t.span(), null));
            } else if (CONDITIONS.contains(lower)) {
                folded.add(new Token(Kind.CONDITION, lower, t.span()));
                diagnostics.add(new Diagnostic(Diagnostic.Severity.ERROR, "keyword-case",
                        "Conditions must be in lowercase: write " + lower + ", not " + t.text(),
                        t.span(), null));
            } else {
                folded.add(t);
            }
        }
        return new Folded(folded, diagnostics);
    }
}
