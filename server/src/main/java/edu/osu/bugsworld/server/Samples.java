package edu.osu.bugsworld.server;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * The example bugs from the repository's samples/ folder, bundled into the
 * server as resources (see server/build.gradle.kts).
 */
final class Samples {

    /** An example bug: its file name without {@code .bl}, and its source. */
    record Sample(String name, String source) {
    }

    private static final List<Sample> ALL = load();

    private Samples() {
    }

    static List<Sample> all() {
        return ALL;
    }

    static Optional<Sample> named(String name) {
        return ALL.stream().filter(s -> s.name().equals(name)).findFirst();
    }

    private static List<Sample> load() {
        List<Sample> samples = new ArrayList<>();
        String index = read("/samples/index.txt");
        if (index == null) {
            return List.of();
        }
        for (String file : index.strip().split("\\s+")) {
            String source = read("/samples/" + file);
            if (source != null) {
                samples.add(new Sample(file.replaceFirst("\\.bl$", ""), source));
            }
        }
        return List.copyOf(samples);
    }

    private static String read(String resource) {
        try (InputStream in = Samples.class.getResourceAsStream(resource)) {
            return in == null ? null : new String(in.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
