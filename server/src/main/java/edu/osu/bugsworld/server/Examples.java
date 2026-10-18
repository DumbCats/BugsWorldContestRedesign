package edu.osu.bugsworld.server;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/**
 * The example bugs from the repository's samples/ folder, bundled into the
 * server (see server/build.gradle.kts). The professor can add them as
 * submissions to try a simulation before any student has submitted.
 */
final class Examples {

    /** An example program: its file name without {@code .bl}, and its source. */
    record Example(String name, String source) {
    }

    private Examples() {
    }

    static List<Example> all() {
        String index = read("/examples/index.txt");
        if (index == null || index.isBlank()) {
            return List.of();
        }
        List<Example> examples = new ArrayList<>();
        for (String file : index.strip().split("\\s+")) {
            String source = read("/examples/" + file);
            if (source != null) {
                examples.add(new Example(file.replaceFirst("\\.bl$", ""), source));
            }
        }
        return List.copyOf(examples);
    }

    private static String read(String resource) {
        try (InputStream in = Examples.class.getResourceAsStream(resource)) {
            return in == null ? null : new String(in.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
