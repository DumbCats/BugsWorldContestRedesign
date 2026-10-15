package edu.osu.bugsworld.engine.compiler;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.junit.jupiter.api.Test;

class WarningsTest {

    /** Warning codes for a program that must compile. */
    private static List<String> warnings(String declarations, String body) {
        CompileResult r = Compiler.compile(
                "PROGRAM W IS " + declarations + " BEGIN " + body + " END W");
        assertTrue(r.succeeded(), () -> r.errors().toString());
        return r.warnings().stream().map(Diagnostic::code).toList();
    }

    @Test
    void loopThatAlwaysActsIsFine() {
        assertEquals(List.of(), warnings("",
                "WHILE true DO IF next-is-enemy THEN infect ELSE move END IF END WHILE"));
    }

    @Test
    void loopWithAPathThatDoesNothing() {
        assertEquals(List.of("silent-loop"), warnings("",
                "WHILE true DO IF next-is-enemy THEN infect END IF END WHILE"));
    }

    @Test
    void emptyLoopBody() {
        assertEquals(List.of("silent-loop", "program-can-halt"),
                warnings("", "WHILE next-is-wall DO END WHILE"));
    }

    @Test
    void ifTrueAlwaysTakesTheThenBranch() {
        assertEquals(List.of(), warnings("", "WHILE true DO IF true THEN move END IF END WHILE"));
    }

    @Test
    void silenceThroughACall() {
        assertEquals(List.of("silent-loop"), warnings(
                "INSTRUCTION maybe IS IF random THEN move END IF END maybe",
                "WHILE true DO maybe END WHILE"));
    }

    @Test
    void innerWhileTrueNeverFinishesSoTheOuterLoopIsNotSilent() {
        assertEquals(List.of(), warnings("",
                "WHILE true DO IF random THEN WHILE true DO move END WHILE END IF skip END WHILE"));
    }

    @Test
    void programThatEnds() {
        assertEquals(List.of("program-can-halt"), warnings("", "move turnleft"));
    }

    @Test
    void neverCalledInstructionsAreWarningsEvenIfBroken() {
        // The reference accepts these, so they must not be errors.
        assertEquals(List.of("unused-instruction", "undefined-instruction", "recursion",
                "unused-instruction"), warnings(
                        "INSTRUCTION orphan IS ghost orphan END orphan INSTRUCTION spare IS skip END spare",
                        "WHILE true DO move END WHILE"));
    }
}
