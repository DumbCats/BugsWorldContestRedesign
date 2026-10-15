package edu.osu.bugsworld.server;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * SQLite storage for submissions and finished-game results, so nothing is
 * lost if the professor's computer restarts mid-lab. One connection, with
 * every method synchronized: plenty for a classroom.
 *
 * <p>Only BL source is stored; it is recompiled when used, so stored
 * programs always match the current compiler.
 */
public final class Database implements AutoCloseable {

    /** A stored submission. */
    public record Submission(long id, String student, String programName, String source,
            Instant createdAt) {
    }

    /** A finished game: when, and its final {@link GameView} as JSON. */
    public record Result(Instant finishedAt, String json) {
    }

    /** A submission without its source, for lists. */
    public record SubmissionSummary(long id, String student, String programName,
            Instant createdAt) {
    }

    private final Connection connection;

    private Database(Connection connection) {
        this.connection = connection;
    }

    /** Opens (creating if needed) the database file {@code dir/bugsworld.db}. */
    public static Database open(Path dir) {
        try {
            Files.createDirectories(dir);
            Connection c = DriverManager.getConnection(
                    "jdbc:sqlite:" + dir.resolve("bugsworld.db").toAbsolutePath());
            try (Statement s = c.createStatement()) {
                s.execute("PRAGMA journal_mode=WAL");
                s.execute("""
                        CREATE TABLE IF NOT EXISTS submissions (
                            id INTEGER PRIMARY KEY AUTOINCREMENT,
                            student TEXT NOT NULL,
                            program_name TEXT NOT NULL,
                            source TEXT NOT NULL,
                            created_at TEXT NOT NULL)""");
                s.execute("""
                        CREATE TABLE IF NOT EXISTS results (
                            id INTEGER PRIMARY KEY AUTOINCREMENT,
                            finished_at TEXT NOT NULL,
                            result_json TEXT NOT NULL)""");
            }
            return new Database(c);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        } catch (SQLException e) {
            throw new IllegalStateException("Can't open the database in " + dir, e);
        }
    }

    public synchronized Submission addSubmission(String student, String programName,
            String source) {
        Instant now = Instant.now();
        try (PreparedStatement s = connection.prepareStatement(
                "INSERT INTO submissions (student, program_name, source, created_at) "
                        + "VALUES (?, ?, ?, ?)",
                Statement.RETURN_GENERATED_KEYS)) {
            s.setString(1, student);
            s.setString(2, programName);
            s.setString(3, source);
            s.setString(4, now.toString());
            s.executeUpdate();
            try (ResultSet keys = s.getGeneratedKeys()) {
                keys.next();
                return new Submission(keys.getLong(1), student, programName, source, now);
            }
        } catch (SQLException e) {
            throw new IllegalStateException(e);
        }
    }

    /** All submissions, newest first. */
    public synchronized List<SubmissionSummary> submissions() {
        List<SubmissionSummary> list = new ArrayList<>();
        try (PreparedStatement s = connection.prepareStatement(
                "SELECT id, student, program_name, created_at FROM submissions ORDER BY id DESC");
                ResultSet r = s.executeQuery()) {
            while (r.next()) {
                list.add(new SubmissionSummary(r.getLong(1), r.getString(2), r.getString(3),
                        Instant.parse(r.getString(4))));
            }
            return list;
        } catch (SQLException e) {
            throw new IllegalStateException(e);
        }
    }

    public synchronized Optional<Submission> submission(long id) {
        try (PreparedStatement s = connection.prepareStatement(
                "SELECT id, student, program_name, source, created_at FROM submissions WHERE id = ?")) {
            s.setLong(1, id);
            try (ResultSet r = s.executeQuery()) {
                if (!r.next()) {
                    return Optional.empty();
                }
                return Optional.of(new Submission(r.getLong(1), r.getString(2), r.getString(3),
                        r.getString(4), Instant.parse(r.getString(5))));
            }
        } catch (SQLException e) {
            throw new IllegalStateException(e);
        }
    }

    public synchronized boolean deleteSubmission(long id) {
        try (PreparedStatement s = connection.prepareStatement(
                "DELETE FROM submissions WHERE id = ?")) {
            s.setLong(1, id);
            return s.executeUpdate() > 0;
        } catch (SQLException e) {
            throw new IllegalStateException(e);
        }
    }

    public synchronized void addResult(String resultJson) {
        try (PreparedStatement s = connection.prepareStatement(
                "INSERT INTO results (finished_at, result_json) VALUES (?, ?)")) {
            s.setString(1, Instant.now().toString());
            s.setString(2, resultJson);
            s.executeUpdate();
        } catch (SQLException e) {
            throw new IllegalStateException(e);
        }
    }

    /** Finished games, newest first. */
    public synchronized List<Result> results() {
        List<Result> list = new ArrayList<>();
        try (PreparedStatement s = connection.prepareStatement(
                "SELECT finished_at, result_json FROM results ORDER BY id DESC");
                ResultSet r = s.executeQuery()) {
            while (r.next()) {
                list.add(new Result(Instant.parse(r.getString(1)), r.getString(2)));
            }
            return list;
        } catch (SQLException e) {
            throw new IllegalStateException(e);
        }
    }

    @Override
    public synchronized void close() {
        try {
            connection.close();
        } catch (SQLException e) {
            throw new IllegalStateException(e);
        }
    }
}
