package edu.osu.bugsworld.engine.compiler;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.junit.jupiter.api.Test;

/** Error messages for common student mistakes: right place, right code, useful hint. */
class DiagnosticsTest {

    private static String bl(String... lines) {
        return String.join("\n", lines) + "\n";
    }

    /** Errors as "line:code" strings, in order. */
    private static List<String> errors(String source) {
        CompileResult r = Compiler.compile(source);
        assertFalse(r.succeeded(), "expected a compile error");
        return r.errors().stream().map(d -> d.span().line() + ":" + d.code()).toList();
    }

    private static Diagnostic onlyError(String source) {
        List<Diagnostic> errors = Compiler.compile(source).errors();
        assertEquals(1, errors.size(), errors::toString);
        return errors.getFirst();
    }

    @Test
    void reportsSeveralIndependentErrorsAtOnce() {
        String src = bl(
                "PROGRAM Multi IS",
                "  INSTRUCTION spin IS",
                "    turnleft",
                "  END spin",
                "BEGIN",
                "  WHILE true DO",
                "    IF next-is-emtpy THEN",
                "      mvoe",
                "    ELSE",
                "      spinn",
                "    END IF",
                "  END WHILE",
                "END Multi");
        assertEquals(List.of("7:unknown-condition", "8:undefined-instruction",
                "10:undefined-instruction"), errors(src));
        List<String> hints = Compiler.compile(src).errors().stream().map(Diagnostic::hint).toList();
        assertEquals(List.of("Did you mean next-is-empty?", "Did you mean move?",
                "Did you mean spin?"), hints);
    }

    @Test
    void lowercaseKeywordsGetOneClearErrorEach() {
        String src = bl(
                "PROGRAM P IS",
                "BEGIN",
                "  IF next-is-enemy then",
                "    infect",
                "  else",
                "    move",
                "  end if",
                "END P");
        assertEquals(List.of("3:keyword-case", "5:keyword-case", "7:keyword-case", "7:keyword-case"),
                errors(src));
    }

    @Test
    void lowercaseNameThatIsDefinedIsNotAKeyword() {
        String src = bl(
                "PROGRAM P IS",
                "  INSTRUCTION end IS",
                "    move",
                "  END end",
                "BEGIN",
                "  end",
                "END P");
        assertTrue(Compiler.compile(src).succeeded());
    }

    @Test
    void missingEndIfInsideWhilePointsAtTheIf() {
        Diagnostic d = onlyError(bl(
                "PROGRAM P IS",
                "BEGIN",
                "  WHILE true DO",
                "    IF next-is-enemy THEN",
                "      infect",
                "  END WHILE",
                "END P"));
        assertEquals("unclosed", d.code());
        assertEquals(new Span(39, 2, 4, 5), d.span());
        assertEquals("Add END IF after its last statement", d.hint());
    }

    @Test
    void wrongClosingKeywordIsAMismatchNotAnUnclosedBlock() {
        Diagnostic d = onlyError(bl(
                "PROGRAM P IS",
                "BEGIN",
                "  IF true THEN",
                "    move",
                "  END WHILE",
                "END P"));
        assertEquals("mismatched-end", d.code());
        assertEquals("Change it to END IF", d.hint());
    }

    @Test
    void unterminatedWhileBeforeProgramEnd() {
        assertEquals(List.of("3:unclosed"), errors(bl(
                "PROGRAM P IS",
                "BEGIN",
                "  WHILE true DO",
                "    move",
                "END P")));
    }

    @Test
    void missingProgramEndAtEndOfFile() {
        assertEquals(List.of("1:unclosed"), errors(bl(
                "PROGRAM P IS",
                "BEGIN",
                "  WHILE true DO",
                "    move",
                "  END WHILE")));
    }

    @Test
    void unclosedInstructionBeforeNextInstruction() {
        assertEquals(List.of("2:unclosed"), errors(bl(
                "PROGRAM P IS",
                "  INSTRUCTION a IS",
                "    move",
                "  INSTRUCTION b IS",
                "    skip",
                "  END b",
                "BEGIN",
                "  a",
                "  b",
                "END P")));
    }

    @Test
    void missingBegin() {
        assertEquals(List.of("2:expected-token"), errors(bl("PROGRAM P IS", "  move", "END P")));
    }

    @Test
    void missingThen() {
        assertEquals(List.of("4:expected-token"), errors(bl(
                "PROGRAM P IS", "BEGIN", "  IF true", "    move", "  END IF", "END P")));
    }

    @Test
    void thenAndDoSwapped() {
        String src = bl(
                "PROGRAM P IS",
                "BEGIN",
                "  WHILE true THEN",
                "    IF random DO",
                "      move",
                "    END IF",
                "  END WHILE",
                "END P");
        assertEquals(List.of("3:expected-token", "4:expected-token"), errors(src));
        assertEquals("WHILE uses DO, not THEN", Compiler.compile(src).errors().get(0).message());
    }

    @Test
    void notAndAndAreExplained() {
        String src = bl(
                "PROGRAM P IS",
                "BEGIN",
                "  IF not next-is-empty THEN",
                "    turnleft",
                "  END IF",
                "  WHILE next-is-empty and next-is-not-wall DO",
                "    move",
                "  END WHILE",
                "END P");
        List<Diagnostic> errors = Compiler.compile(src).errors();
        assertEquals(2, errors.size(), errors::toString);
        assertEquals("Write next-is-not-empty instead", errors.get(0).hint());
        assertEquals("Put one IF inside another instead", errors.get(1).hint());
    }

    @Test
    void commentsAreOneErrorForTheWholeLine() {
        Diagnostic d = onlyError(bl(
                "PROGRAM P IS",
                "BEGIN",
                "  move # turn around and look for enemies",
                "  skip",
                "END P"));
        assertEquals("comment", d.code());
        assertEquals(3, d.span().line());
        assertEquals("# turn around and look for enemies".length(), d.span().length());
    }

    @Test
    void underscoreSuggestsDash() {
        Diagnostic d = Compiler.compile(bl(
                "PROGRAM P IS",
                "BEGIN",
                "  go_now",
                "END P")).errors().getFirst();
        assertEquals("invalid-name", d.code());
        assertEquals("Use '-' instead, e.g. go-now", d.hint());
    }

    @Test
    void invisibleCharacterIsNamed() {
        Diagnostic d = onlyError(bl("PROGRAM P IS", "BEGIN", "  move skip", "END P"));
        assertTrue(d.message().contains("U+00A0"), d.message());
    }

    @Test
    void primitivesAreCaseSensitive() {
        Diagnostic d = onlyError(bl("PROGRAM P IS", "BEGIN", "  Move", "END P"));
        assertEquals("undefined-instruction", d.code());
        assertEquals("Did you mean move? Names are case-sensitive.", d.hint());
    }

    @Test
    void instructionAfterBeginIsReportedButStillResolves() {
        assertEquals(List.of("4:misplaced-instruction"), errors(bl(
                "PROGRAM P IS",
                "BEGIN",
                "  go",
                "  INSTRUCTION go IS",
                "    move",
                "  END go",
                "END P")));
    }

    @Test
    void strayElseAndStrayEnd() {
        assertEquals(List.of("4:unexpected-token", "6:unexpected-token"), errors(bl(
                "PROGRAM P IS",
                "BEGIN",
                "  move",
                "  ELSE",
                "  skip",
                "  END IF",
                "END P")));
    }

    @Test
    void conditionUsedAsAStatement() {
        assertEquals(List.of("3:unexpected-token"), errors(bl(
                "PROGRAM P IS", "BEGIN", "  next-is-empty", "  move", "END P")));
    }

    @Test
    void recursionShowsTheCycle() {
        Diagnostic d = onlyError(bl(
                "PROGRAM P IS",
                "  INSTRUCTION a IS",
                "    b",
                "  END a",
                "  INSTRUCTION b IS",
                "    c",
                "  END b",
                "  INSTRUCTION c IS",
                "    a",
                "  END c",
                "BEGIN",
                "  a",
                "END P"));
        assertEquals("recursion", d.code());
        assertTrue(d.message().contains("(a -> b -> c -> a)"), d.message());
    }

    @Test
    void keywordNamedInstructionStillClosesCleanly() {
        assertEquals(List.of("2:invalid-name"), errors(bl(
                "PROGRAM P IS",
                "  INSTRUCTION WHILE IS",
                "    move",
                "  END WHILE",
                "BEGIN",
                "  move",
                "END P")));
    }

    @Test
    void garbageAfterTheProgramIsOneError() {
        Diagnostic d = onlyError(bl("PROGRAM P IS", "BEGIN", "END P", "move", "skip skip"));
        assertEquals("trailing-input", d.code());
        assertEquals(4, d.span().line());
    }

    @Test
    void pathologicalInputStaysBounded() {
        String src = "THEN ".repeat(5000);
        CompileResult r = Compiler.compile(src);
        assertFalse(r.succeeded());
        assertTrue(r.diagnostics().size() <= Diagnostics.MAX);
    }

    @Test
    void exponentialInliningIsCapped() {
        StringBuilder src = new StringBuilder("PROGRAM B IS\n  INSTRUCTION i0 IS move move END i0\n");
        for (int i = 1; i <= 20; i++) {
            src.append("  INSTRUCTION i").append(i).append(" IS i").append(i - 1).append(" i")
                    .append(i - 1).append(" END i").append(i).append('\n');
        }
        src.append("BEGIN\n  i20\nEND B\n");
        Diagnostic d = Compiler.compile(src.toString(), 1000).errors().getFirst();
        assertEquals("code-too-large", d.code());
        assertTrue(d.message().contains("2097153"), d.message());
    }
}
