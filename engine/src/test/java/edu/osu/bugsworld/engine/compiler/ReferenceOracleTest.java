package edu.osu.bugsworld.engine.compiler;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/**
 * Differential test against the OSU reference compiler: on random programs
 * and random corruptions of them, both compilers must accept the same
 * programs and produce identical object code.
 *
 * <p>Skipped when lib/BWClient.jar is absent. Tune with
 * {@code -Dbugsworld.fuzzCases=N} and {@code -Dbugsworld.fuzzSeed=S}.
 */
class ReferenceOracleTest {

    private static ReferenceCompiler reference;

    @BeforeAll
    static void loadReference() throws Exception {
        String jar = System.getProperty("bugsworld.referenceJar", "");
        assumeTrue(!jar.isEmpty() && Files.exists(Path.of(jar)), "reference jar not available");
        reference = new ReferenceCompiler(Path.of(jar));
    }

    @AfterAll
    static void closeReference() throws Exception {
        if (reference != null) {
            reference.close();
        }
    }

    @Test
    void acceptsTheSameProgramsAndGeneratesTheSameCode() throws Exception {
        int cases = Integer.getInteger("bugsworld.fuzzCases", 3000);
        long seed = Long.getLong("bugsworld.fuzzSeed", 20261015L);
        RandomPrograms programs = new RandomPrograms(seed);
        int accepted = 0;
        int rejected = 0;
        for (int i = 0; i < cases; i++) {
            String valid = programs.program();
            String source = i % 2 == 0 ? valid : programs.mutate(valid);
            Optional<int[]> expected = reference.compile(source);
            CompileResult actual = Compiler.compile(source);
            String context = "case " + i + " (seed " + seed + "):\n" + source + "\nours: "
                    + actual.diagnostics();
            assertEquals(expected.isPresent(), actual.succeeded(), context);
            if (expected.isPresent()) {
                assertArrayEquals(expected.get(), actual.code().toArray(), context);
                accepted++;
            } else {
                assertTrue(!actual.errors().isEmpty(), context);
                rejected++;
            }
        }
        // Make sure the generator exercises both outcomes.
        assertTrue(accepted > cases / 3, "accepted only " + accepted);
        assertTrue(rejected > cases / 10, "rejected only " + rejected);
    }
}
