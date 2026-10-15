package edu.osu.bugsworld.engine;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.URISyntaxException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Stream;

/**
 * Golden test cases in src/test/resources/golden, produced by the OSU
 * reference compiler via tools/oracle/regen-golden.sh.
 *
 * <p>{@code valid/} holds {@code .bl} + expected {@code .bo};
 * {@code errors/} holds {@code .bl} + the reference error message
 * ({@code .err}). Our messages are meant to be better, so tests should check
 * that an error is reported, not that the text matches.
 */
public final class Golden {

    public record Case(String name, String source, String expectedBo, String referenceError) {
    }

    private Golden() {
    }

    public static List<Case> valid() throws IOException {
        return load("valid");
    }

    public static List<Case> errors() throws IOException {
        return load("errors");
    }

    private static List<Case> load(String dir) throws IOException {
        Path root;
        try {
            root = Path.of(Golden.class.getResource("/golden/" + dir).toURI());
        } catch (URISyntaxException e) {
            throw new IllegalStateException(e);
        }
        try (Stream<Path> files = Files.list(root)) {
            return files
                    .filter(p -> p.toString().endsWith(".bl"))
                    .sorted()
                    .map(Golden::toCase)
                    .toList();
        }
    }

    private static Case toCase(Path bl) {
        String base = bl.getFileName().toString().replaceFirst("\\.bl$", "");
        return new Case(base, read(bl), readIfExists(bl.resolveSibling(base + ".bo")),
                readIfExists(bl.resolveSibling(base + ".err")));
    }

    private static String readIfExists(Path p) {
        return Files.exists(p) ? read(p) : null;
    }

    private static String read(Path p) {
        try {
            return Files.readString(p);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
