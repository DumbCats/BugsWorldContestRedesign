package edu.osu.bugsworld.engine.compiler;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import edu.osu.bugsworld.engine.Golden;
import edu.osu.bugsworld.engine.ObjectCode;
import java.io.IOException;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestFactory;

/** Our compiler against the reference compiler's recorded output. */
class GoldenCompilerTest {

    /** For each golden error case: the line and code of our first error. */
    private static final Map<String, Expected> FIRST_ERROR = Map.ofEntries(
            Map.entry("comment", new Expected(3, "comment")),
            Map.entry("condition-as-name", new Expected(2, "invalid-name")),
            Map.entry("digit-first-name", new Expected(1, "invalid-name")),
            Map.entry("direct-recursion", new Expected(3, "recursion")),
            Map.entry("duplicate-instruction", new Expected(5, "duplicate-instruction")),
            Map.entry("empty-file", new Expected(2, "expected-token")),
            Map.entry("end-if-mismatch", new Expected(5, "mismatched-end")),
            Map.entry("instruction-name-mismatch", new Expected(4, "end-name-mismatch")),
            Map.entry("keyword-as-name", new Expected(2, "invalid-name")),
            Map.entry("lowercase-keyword", new Expected(1, "keyword-case")),
            Map.entry("missing-then", new Expected(4, "expected-token")),
            Map.entry("mutual-recursion", new Expected(6, "recursion")),
            Map.entry("primitive-as-name", new Expected(2, "primitive-name")),
            Map.entry("program-name-mismatch", new Expected(4, "end-name-mismatch")),
            Map.entry("trailing-tokens", new Expected(5, "trailing-input")),
            Map.entry("undefined-call", new Expected(3, "undefined-instruction")),
            Map.entry("underscore", new Expected(2, "invalid-name")),
            Map.entry("unknown-condition", new Expected(3, "unknown-condition")),
            Map.entry("unterminated", new Expected(3, "unclosed")));

    private record Expected(int line, String code) {
    }

    @TestFactory
    List<DynamicTest> validProgramsMatchReferenceCode() throws IOException {
        return Golden.valid().stream().map(c -> DynamicTest.dynamicTest(c.name(), () -> {
            CompileResult r = Compiler.compile(c.source());
            assertTrue(r.errors().isEmpty(), () -> "unexpected errors: " + r.errors());
            assertEquals(ObjectCode.parseBo(c.expectedBo()), r.code());
            assertEquals(r.code().length(), r.sourceMap().size());
        })).toList();
    }

    @TestFactory
    List<DynamicTest> formattingKeepsTheSameCode() throws IOException {
        return Golden.valid().stream().map(c -> DynamicTest.dynamicTest(c.name(), () -> {
            CompileResult r = Compiler.compile(c.source());
            String formatted = r.formatted();
            CompileResult again = Compiler.compile(formatted);
            assertEquals(r.code(), again.code(), formatted);
            assertEquals(formatted, again.formatted(), "formatting is idempotent");
        })).toList();
    }

    @TestFactory
    List<DynamicTest> invalidProgramsAreRejectedAtTheRightPlace() throws IOException {
        return Golden.errors().stream().map(c -> DynamicTest.dynamicTest(c.name(), () -> {
            assertNotNull(c.referenceError(), "golden case without .err");
            CompileResult r = Compiler.compile(c.source());
            assertFalse(r.succeeded());
            Expected expected = FIRST_ERROR.get(c.name());
            assertNotNull(expected, "add " + c.name() + " to FIRST_ERROR");
            Diagnostic first = r.errors().getFirst();
            assertEquals(expected, new Expected(first.span().line(), first.code()), first.toString());
        })).toList();
    }

    @Test
    void everyErrorCaseIsListed() throws IOException {
        assertEquals(FIRST_ERROR.size(), Golden.errors().size());
    }
}
