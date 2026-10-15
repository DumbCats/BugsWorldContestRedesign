package edu.osu.bugsworld.engine.compiler;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.junit.jupiter.api.Test;

/** Code generation patterns (reference-behavior.md §7) and the source map. */
class CodeGenTest {

    private static int[] code(String body, String... instructions) {
        String src = "PROGRAM T IS " + String.join(" ", instructions) + " BEGIN " + body + " END T";
        CompileResult r = Compiler.compile(src);
        assertTrue(r.succeeded(), () -> r.errors().toString());
        return r.code().toArray();
    }

    @Test
    void primitives() {
        assertArrayEquals(new int[] {0, 1, 2, 3, 4, 5}, code("move turnleft turnright infect skip"));
    }

    @Test
    void ifJumpsPastBodyWhenFalse() {
        assertArrayEquals(new int[] {7, 3, 0, 5}, code("IF next-is-empty THEN move END IF"));
    }

    @Test
    void ifElse() {
        // JIN_ENEMY 7; infect; JUMP 8; turnleft; HALT
        assertArrayEquals(new int[] {13, 5, 3, 6, 6, 1, 5},
                code("IF next-is-enemy THEN infect ELSE turnleft END IF"));
    }

    @Test
    void whileLoopsBackToItsTest() {
        assertArrayEquals(new int[] {16, 5, 4, 6, 0, 5}, code("WHILE true DO skip END WHILE"));
    }

    @Test
    void nestedStatementsUseAbsoluteAddresses() {
        // 0: JIN_TRUE 10 | 2: JIN_EMPTY 7 | 4: move | 5: JUMP 8 | 7: turnright
        // 8: JUMP 0 | 10: HALT
        assertArrayEquals(new int[] {16, 10, 7, 7, 0, 6, 8, 2, 6, 0, 5},
                code("WHILE true DO IF next-is-empty THEN move ELSE turnright END IF END WHILE"));
    }

    @Test
    void callsAreInlinedAndUnusedInstructionsVanish() {
        assertArrayEquals(new int[] {0, 2, 3, 0, 2, 5},
                code("two infect two",
                        "INSTRUCTION two IS move turnright END two",
                        "INSTRUCTION unused IS skip END unused"));
    }

    @Test
    void loopBackJumpSurvivesBufferGrowth() {
        // Regression: the back-jump target was written to the old array when
        // emitting the JUMP grew the buffer (64 ints initially).
        for (int n = 55; n <= 70; n++) {
            int[] code = code("WHILE true DO " + "move ".repeat(n) + "END WHILE");
            assertEquals(6, code[n + 2], "JUMP for n=" + n);
            assertEquals(0, code[n + 3], "back-jump target for n=" + n);
            assertEquals(n + 4, code[1], "exit target for n=" + n);
        }
    }

    @Test
    void everyConditionMapsToItsJump() {
        for (Condition c : Condition.values()) {
            int[] code = code("IF " + c.text() + " THEN skip END IF");
            assertEquals(c.jumpIfNot().code(), code[0], c.text());
        }
    }

    @Test
    void sourceMapPointsIntoInstructionBodies() {
        String src = String.join("\n",
                "PROGRAM T IS",
                "  INSTRUCTION hop IS",
                "    move",
                "  END hop",
                "BEGIN",
                "  WHILE true DO",
                "    hop",
                "  END WHILE",
                "END T");
        CompileResult r = Compiler.compile(src);
        List<Integer> lines = r.sourceMap().stream().map(Span::line).toList();
        // JIN_TRUE x2 (line 6), move (line 3, inside hop), JUMP x2 (line 6), HALT (line 9)
        assertEquals(List.of(6, 6, 3, 6, 6, 9), lines);
    }
}
