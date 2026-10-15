package edu.osu.bugsworld.engine.sim;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import edu.osu.bugsworld.engine.compiler.CompileResult;
import edu.osu.bugsworld.engine.compiler.Compiler;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Stream;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.TestFactory;

/** The sample bugs in samples/ must compile cleanly: they are examples for students. */
class SamplesTest {

    static List<Path> samples() throws IOException {
        Path dir = Path.of(System.getProperty("bugsworld.samplesDir", "../samples"));
        try (Stream<Path> files = Files.list(dir)) {
            return files.filter(p -> p.toString().endsWith(".bl")).sorted().toList();
        }
    }

    @TestFactory
    List<DynamicTest> compileWithoutErrorsOrWarnings() throws IOException {
        List<Path> samples = samples();
        assertFalse(samples.isEmpty());
        return samples.stream().map(p -> DynamicTest.dynamicTest(p.getFileName().toString(), () -> {
            CompileResult r = Compiler.compile(Files.readString(p));
            assertTrue(r.succeeded(), () -> r.errors().toString());
            assertEquals(List.of(), r.warnings());
            assertEquals(Files.readString(p), r.formatted(), "samples use the standard format");
        })).toList();
    }
}
