package edu.osu.bugsworld.server;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

/**
 * Stores submissions and finished-run results in one readable JSON file,
 * {@code bugsworld.json} in the data folder, so nothing is lost if the
 * professor's computer restarts mid-lab.
 *
 * <p>Everything is kept in memory and the whole file is rewritten after
 * each change: written to a temporary file, then moved into place, so a crash
 * can never leave a half-written file. A class produces at most a few
 * hundred small programs, so this is plenty, with no native libraries.
 *
 * <p>Only BL source is stored; it is recompiled when used, so stored
 * programs always match the current compiler.
 */
public final class Database implements AutoCloseable {

    /** A stored submission. */
    public record Submission(long id, String student, String programName, String source,
            Instant createdAt) {
    }

    /** A finished run: when, and its final {@link GameView} as JSON. */
    public record Result(Instant finishedAt, String json) {
    }

    /** A submission without its source, for lists. */
    public record SubmissionSummary(long id, String student, String programName,
            Instant createdAt) {
    }

    /** The file's layout. Times are ISO-8601 strings. */
    private record StoredSubmission(long id, String student, String programName, String source,
            String createdAt) {
    }

    private record StoredResult(String finishedAt, JsonNode game) {
    }

    private record Contents(long nextId, List<StoredSubmission> submissions,
            List<StoredResult> results) {
    }

    private static final ObjectMapper MAPPER = new ObjectMapper()
            .enable(SerializationFeature.INDENT_OUTPUT);

    private final Path file;
    private long nextId;
    private final List<StoredSubmission> submissions;
    private final List<StoredResult> results;

    private Database(Path file, Contents contents) {
        this.file = file;
        this.nextId = contents.nextId();
        this.submissions = new ArrayList<>(contents.submissions());
        this.results = new ArrayList<>(contents.results());
    }

    /** Opens (creating if needed) {@code dir/bugsworld.json}. */
    public static Database open(Path dir) {
        Path file = dir.resolve("bugsworld.json");
        try {
            Files.createDirectories(dir);
            Contents contents = new Contents(1, List.of(), List.of());
            if (Files.exists(file)) {
                try {
                    contents = MAPPER.readValue(file.toFile(), Contents.class);
                } catch (IOException e) {
                    // Never destroy data we can't read: keep it aside and start fresh.
                    Path aside = dir.resolve("bugsworld.json.unreadable-" + System.currentTimeMillis());
                    Files.move(file, aside);
                    System.err.println("Couldn't read " + file + " (" + e.getMessage()
                            + "); kept it as " + aside + " and started a new one.");
                }
            }
            return new Database(file, contents);
        } catch (IOException e) {
            throw new UncheckedIOException("Can't use the data folder " + dir, e);
        }
    }

    public synchronized Submission addSubmission(String student, String programName,
            String source) {
        Instant now = Instant.now();
        StoredSubmission s = new StoredSubmission(nextId++, student, programName, source,
                now.toString());
        submissions.add(s);
        save();
        return toSubmission(s);
    }

    /** All submissions, newest first. */
    public synchronized List<SubmissionSummary> submissions() {
        return submissions.stream()
                .sorted(Comparator.comparingLong(StoredSubmission::id).reversed())
                .map(s -> new SubmissionSummary(s.id(), s.student(), s.programName(),
                        Instant.parse(s.createdAt())))
                .toList();
    }

    public synchronized Optional<Submission> submission(long id) {
        return submissions.stream().filter(s -> s.id() == id).findFirst()
                .map(Database::toSubmission);
    }

    public synchronized boolean deleteSubmission(long id) {
        boolean removed = submissions.removeIf(s -> s.id() == id);
        if (removed) {
            save();
        }
        return removed;
    }

    public synchronized void addResult(String resultJson) {
        try {
            results.add(new StoredResult(Instant.now().toString(), MAPPER.readTree(resultJson)));
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        save();
    }

    /** Finished runs, newest first. */
    public synchronized List<Result> results() {
        List<Result> list = new ArrayList<>();
        for (int i = results.size() - 1; i >= 0; i--) {
            StoredResult r = results.get(i);
            list.add(new Result(Instant.parse(r.finishedAt()), r.game().toString()));
        }
        return list;
    }

    @Override
    public synchronized void close() {
        // Every change is already on disk.
    }

    private static Submission toSubmission(StoredSubmission s) {
        return new Submission(s.id(), s.student(), s.programName(), s.source(),
                Instant.parse(s.createdAt()));
    }

    /** Writes everything to a temporary file, then moves it into place. */
    private void save() {
        Path temp = file.resolveSibling(file.getFileName() + ".tmp");
        try {
            MAPPER.writeValue(temp.toFile(), new Contents(nextId, submissions, results));
            try {
                Files.move(temp, file, StandardCopyOption.REPLACE_EXISTING,
                        StandardCopyOption.ATOMIC_MOVE);
            } catch (AtomicMoveNotSupportedException e) {
                Files.move(temp, file, StandardCopyOption.REPLACE_EXISTING);
            }
        } catch (IOException e) {
            throw new UncheckedIOException("Couldn't save " + file, e);
        }
    }
}
