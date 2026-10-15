package edu.osu.bugsworld.server;

import edu.osu.bugsworld.engine.sim.Game;
import edu.osu.bugsworld.engine.sim.GameConfig;
import edu.osu.bugsworld.engine.sim.Species;
import edu.osu.bugsworld.engine.vm.BugProgram;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

/**
 * Runs the current game on its own thread. Every command is queued to that
 * thread, so the engine (which isn't thread-safe) needs no locks, and HTTP
 * handlers simply wait for the result. After every change the new
 * {@link GameView} goes to {@code publish}.
 *
 * <p>There is one current game at a time, like the jar server. Creating a
 * new one ends the old one.
 */
public final class GameRunner implements AutoCloseable {

    public static final int MIN_SPEED = 1;
    public static final int MAX_SPEED = 100;
    public static final int DEFAULT_SPEED = 10; // the jar ticked every 100 ms

    private record Entry(String name, long submissionId, BugProgram program) {
    }

    private final ScheduledExecutorService thread = Executors.newSingleThreadScheduledExecutor(
            r -> new Thread(r, "bugsworld-game"));
    private final Consumer<GameView> publish;
    private final Consumer<GameView> onEnded;

    // Touched only on the game thread.
    private Game game;
    private final Map<Integer, Entry> entries = new LinkedHashMap<>();
    private int gameNumber;
    private int ticksPerSecond = DEFAULT_SPEED;
    private ScheduledFuture<?> ticker;
    private boolean resultRecorded;

    private GameRunner(Consumer<GameView> publish, Consumer<GameView> onEnded) {
        this.publish = publish;
        this.onEnded = onEnded;
    }

    /**
     * Starts the game thread with an empty default game.
     *
     * @param publish called on the game thread with each new view
     * @param onEnded called once when a game that has run at least one tick ends
     */
    public static GameRunner start(Consumer<GameView> publish, Consumer<GameView> onEnded) {
        GameRunner runner = new GameRunner(publish, onEnded);
        runner.call(() -> {
            runner.replaceGame(GameConfig.defaults(randomSeed()));
            return runner.changed();
        });
        return runner;
    }

    public GameView view() {
        return call(this::currentView);
    }

    /** Ends the current game and sets up a new, empty one. */
    public GameView newGame(GameConfig config, int speed) {
        checkSpeed(speed);
        return call(() -> {
            finish();
            ticksPerSecond = speed;
            replaceGame(config);
            return changed();
        });
    }

    public GameView addSpecies(String name, long submissionId, BugProgram program) {
        return call(() -> {
            Species s = game.addSpecies(name, program);
            entries.put(s.id(), new Entry(name, submissionId, program));
            return changed();
        });
    }

    public GameView removeSpecies(int speciesId) {
        return call(() -> {
            game.removeSpecies(speciesId);
            entries.remove(speciesId);
            return changed();
        });
    }

    /** Starts or resumes automatic ticking. */
    public GameView start() {
        return call(() -> {
            requireNotEnded();
            if (game.species().isEmpty()) {
                throw new IllegalStateException("Add at least one species first");
            }
            if (ticker == null) {
                scheduleTicks();
            }
            return changed();
        });
    }

    public GameView pause() {
        return call(() -> {
            stopTicking();
            return changed();
        });
    }

    /** Runs exactly one tick; only while not ticking automatically. */
    public GameView step() {
        return call(() -> {
            if (ticker != null) {
                throw new IllegalStateException("Pause the game before stepping");
            }
            requireNotEnded();
            tickOnce();
            return currentView();
        });
    }

    public GameView end() {
        return call(() -> {
            finish();
            return changed();
        });
    }

    /**
     * A fresh game with the same settings, seed and species (in the order
     * they were added): replays the same game from the start.
     */
    public GameView reset() {
        return call(() -> {
            finish();
            List<Entry> keep = List.copyOf(entries.values());
            replaceGame(game.config());
            for (Entry e : keep) {
                Species s = game.addSpecies(e.name(), e.program());
                entries.put(s.id(), e);
            }
            return changed();
        });
    }

    public GameView setSpeed(int speed) {
        checkSpeed(speed);
        return call(() -> {
            ticksPerSecond = speed;
            if (ticker != null) {
                stopTicking();
                scheduleTicks();
            }
            return changed();
        });
    }

    @Override
    public void close() {
        thread.shutdownNow();
    }

    // ---------------------------------------------------------- game thread

    private void replaceGame(GameConfig config) {
        game = new Game(config);
        entries.clear();
        gameNumber++;
        resultRecorded = false;
    }

    private void scheduleTicks() {
        long periodMicros = 1_000_000L / ticksPerSecond;
        ticker = thread.scheduleAtFixedRate(this::tickSafely, periodMicros, periodMicros,
                TimeUnit.MICROSECONDS);
    }

    private void stopTicking() {
        if (ticker != null) {
            ticker.cancel(false);
            ticker = null;
        }
    }

    private void tickSafely() {
        try {
            tickOnce();
        } catch (RuntimeException e) {
            // An exception would silently cancel the periodic task, which is
            // exactly how the jar froze. Stop cleanly and say why instead.
            stopTicking();
            System.err.println("Game stopped after an internal error:");
            e.printStackTrace();
        }
    }

    private void tickOnce() {
        game.step();
        if (game.phase() == Game.Phase.ENDED) {
            stopTicking();
            recordResult();
        }
        changed();
    }

    /** Stops ticking and ends the game, recording its result if it ran. */
    private void finish() {
        stopTicking();
        if (game.phase() == Game.Phase.RUNNING) {
            game.end();
        }
        recordResult();
    }

    private void recordResult() {
        if (!resultRecorded && game.phase() == Game.Phase.ENDED && game.ticks() > 0) {
            resultRecorded = true;
            onEnded.accept(currentView().withoutBugs());
        }
    }

    private GameView changed() {
        GameView v = currentView();
        publish.accept(v);
        return v;
    }

    private GameView currentView() {
        // Ticking may already be scheduled while the engine is still in SETUP
        // (before its first tick), so check the ticker first.
        String status = game.phase() == Game.Phase.ENDED ? "ended"
                : ticker != null ? "running"
                : game.phase() == Game.Phase.SETUP ? "setup"
                : "paused";
        Map<Integer, Long> submissionIds = new LinkedHashMap<>();
        entries.forEach((id, e) -> submissionIds.put(id, e.submissionId()));
        return GameView.of(gameNumber, status, game.snapshot(), game.config(), ticksPerSecond,
                submissionIds);
    }

    private void requireNotEnded() {
        if (game.phase() == Game.Phase.ENDED) {
            throw new IllegalStateException("This game has ended; reset it or start a new one");
        }
    }

    private static void checkSpeed(int speed) {
        if (speed < MIN_SPEED || speed > MAX_SPEED) {
            throw new IllegalArgumentException(
                    "Speed must be " + MIN_SPEED + " to " + MAX_SPEED + " ticks per second");
        }
    }

    static long randomSeed() {
        // Small enough to read out loud or type into the dashboard.
        return java.util.concurrent.ThreadLocalRandom.current().nextLong(1, 1_000_000);
    }

    /** Runs {@code task} on the game thread and waits for it, rethrowing its exceptions. */
    private <T> T call(Callable<T> task) {
        try {
            return thread.submit(task).get();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Interrupted", e);
        } catch (ExecutionException e) {
            if (e.getCause() instanceof RuntimeException r) {
                throw r;
            }
            throw new IllegalStateException(e.getCause());
        }
    }
}
