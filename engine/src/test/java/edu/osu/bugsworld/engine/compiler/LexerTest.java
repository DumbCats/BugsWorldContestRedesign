package edu.osu.bugsworld.engine.compiler;

import static org.junit.jupiter.api.Assertions.assertEquals;

import edu.osu.bugsworld.engine.compiler.Token.Kind;
import java.util.List;
import org.junit.jupiter.api.Test;

class LexerTest {

    @Test
    void splitsOnlyOnSpaceTabCrLf() {
        List<Token> tokens = Lexer.tokenize("IF\tnext-is-empty  THEN\r\nmove\u000Bskip\fx");
        assertEquals(List.of("IF", "next-is-empty", "THEN", "move\u000Bskip\fx", ""),
                tokens.stream().map(Token::text).toList());
        assertEquals(List.of(Kind.KEYWORD, Kind.CONDITION, Kind.KEYWORD, Kind.INVALID, Kind.EOF),
                tokens.stream().map(Token::kind).toList());
    }

    @Test
    void tracksLinesAndColumns() {
        List<Token> tokens = Lexer.tokenize("PROGRAM P IS\r\n  BEGIN\n\tmove\rEND P");
        assertEquals(new Span(0, 7, 1, 1), tokens.get(0).span());
        assertEquals(new Span(8, 1, 1, 9), tokens.get(1).span());
        assertEquals(new Span(16, 5, 2, 3), tokens.get(3).span());
        assertEquals(new Span(23, 4, 3, 2), tokens.get(4).span());
        assertEquals(new Span(28, 3, 4, 1), tokens.get(5).span());
        assertEquals(new Span(33, 0, 4, 6), tokens.get(7).span()); // EOF
    }

    @Test
    void classifiesLikeTheReferenceTokenizer() {
        assertEquals(Kind.IDENTIFIER, Lexer.classify("move"));
        assertEquals(Kind.IDENTIFIER, Lexer.classify("Move"));
        assertEquals(Kind.IDENTIFIER, Lexer.classify("a-1-"));
        assertEquals(Kind.IDENTIFIER, Lexer.classify("end"));
        assertEquals(Kind.KEYWORD, Lexer.classify("INSTRUCTION"));
        assertEquals(Kind.CONDITION, Lexer.classify("true"));
        assertEquals(Kind.IDENTIFIER, Lexer.classify("TRUE"));
        assertEquals(Kind.INVALID, Lexer.classify("1a"));
        assertEquals(Kind.INVALID, Lexer.classify("-a"));
        assertEquals(Kind.INVALID, Lexer.classify("a_b"));
        assertEquals(Kind.INVALID, Lexer.classify("café"));
    }

    @Test
    void ignoresLeadingByteOrderMark() {
        List<Token> tokens = Lexer.tokenize("﻿PROGRAM");
        assertEquals("PROGRAM", tokens.getFirst().text());
        assertEquals(new Span(1, 7, 1, 1), tokens.getFirst().span());
    }

    @Test
    void foldCaseLeavesDefinedNamesAlone() {
        List<Token> tokens = Lexer.tokenize("INSTRUCTION end IS end if True");
        Lexer.Folded folded = Lexer.foldCase(tokens);
        assertEquals(List.of(Kind.KEYWORD, Kind.IDENTIFIER, Kind.KEYWORD, Kind.IDENTIFIER,
                Kind.KEYWORD, Kind.CONDITION, Kind.EOF),
                folded.tokens().stream().map(Token::kind).toList());
        assertEquals(2, folded.diagnostics().size());
    }
}
