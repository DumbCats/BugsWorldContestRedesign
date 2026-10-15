package edu.osu.bugsworld.engine.sim;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import edu.osu.bugsworld.engine.compiler.CompileResult;
import edu.osu.bugsworld.engine.compiler.Compiler;
import edu.osu.bugsworld.engine.vm.BugProgram;
import java.io.OutputStream;
import java.io.PrintStream;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.net.ServerSocket;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/**
 * Statistical comparison with the jar's game engine. The jar is unseeded, so
 * single games can't be compared; instead both engines play the same matchup
 * many times and the average final bug counts must agree within sampling
 * error. This checks placement, turn order, sensing, movement and infection
 * together.
 *
 * <p>The jar is driven tick by tick through reflection (its own
 * {@code RunningState.oneStep}), not in real time. Matchups use only programs
 * that always act, because anything else freezes the jar.
 */
class EngineReferenceTest {

    /** Games per engine per matchup; raise with -Dbugsworld.referenceGames=N. */
    private static final int GAMES = Integer.getInteger("bugsworld.referenceGames", 300);

    private static URLClassLoader loader;
    private static Object unsafe;
    private static Method allocateInstance;

    @BeforeAll
    static void load() throws Exception {
        String jar = System.getProperty("bugsworld.referenceJar", "");
        assumeTrue(!jar.isEmpty() && Files.exists(Path.of(jar)), "reference jar not available");
        loader = new URLClassLoader(new URL[] {Path.of(jar).toUri().toURL()}, null);
        Class<?> unsafeClass = Class.forName("sun.misc.Unsafe");
        Field theUnsafe = unsafeClass.getDeclaredField("theUnsafe");
        theUnsafe.setAccessible(true);
        unsafe = theUnsafe.get(null);
        allocateInstance = unsafeClass.getMethod("allocateInstance", Class.class);
    }

    @AfterAll
    static void close() throws Exception {
        if (loader != null) {
            loader.close();
        }
    }

    @Test
    void twoSpeciesMatchup() throws Exception {
        compare(15, 15, 50, 100, List.of("hunter", "wanderer"));
    }

    @Test
    void threeSpeciesMatchup() throws Exception {
        compare(12, 12, 30, 150, List.of("turret", "flocker", "hunter"));
    }

    private static void compare(int rows, int columns, int bugs, int ticks, List<String> names)
            throws Exception {
        List<int[]> programs = names.stream().map(EngineReferenceTest::sample).toList();
        double[][] ours = new double[names.size()][GAMES];
        double[][] jar = new double[names.size()][GAMES];
        for (int g = 0; g < GAMES; g++) {
            int[] a = playOurs(rows, columns, bugs, ticks, programs, g);
            int[] b = playJar(rows, columns, bugs, ticks, programs);
            for (int s = 0; s < names.size(); s++) {
                ours[s][g] = a[s];
                jar[s][g] = b[s];
            }
        }
        for (int s = 0; s < names.size(); s++) {
            double meanOurs = mean(ours[s]);
            double meanJar = mean(jar[s]);
            double se = Math.sqrt(variance(ours[s]) / GAMES + variance(jar[s]) / GAMES);
            // 4.5 standard errors: a false alarm about once in 150,000 runs.
            String report = String.format("%s: ours %.2f vs jar %.2f (se %.2f)", names.get(s),
                    meanOurs, meanJar, se);
            System.out.println(report);
            assertTrue(Math.abs(meanOurs - meanJar) <= 4.5 * se + 1e-9, report);
        }
    }

    private static int[] sample(String name) {
        try {
            Path dir = Path.of(System.getProperty("bugsworld.samplesDir", "../samples"));
            CompileResult r = Compiler.compile(Files.readString(dir.resolve(name + ".bl")));
            assertTrue(r.succeeded() && r.warnings().isEmpty(), name + " must always act");
            return r.code().toArray();
        } catch (java.io.IOException e) {
            throw new java.io.UncheckedIOException(e);
        }
    }

    private static int[] playOurs(int rows, int columns, int bugs, int ticks,
            List<int[]> programs, long seed) {
        Game game = new Game(GameConfig.defaults(seed).withSize(rows, columns)
                .withBugsPerSpecies(bugs).withMaxTicks(ticks));
        for (int[] code : programs) {
            game.addSpecies("s", BugProgram.of(edu.osu.bugsworld.engine.ObjectCode.of(code)));
        }
        Snapshot s = game.snapshot();
        while (s.phase() != Game.Phase.ENDED) {
            s = game.step();
        }
        return s.species().stream().mapToInt(SpeciesStats::current).toArray();
    }

    private static int[] playJar(int rows, int columns, int bugs, int ticks, List<int[]> programs)
            throws Exception {
        Class<?> gameClass = loader.loadClass("bugsworld.Game");
        Class<?> paramsClass = loader.loadClass("bugsworld.Game$GameParams");
        Class<?> playerClass = loader.loadClass("bugsworld.Player");
        Class<?> bugClass = loader.loadClass("bugsworld.Bug");
        Object params = paramsClass.getConstructor(int.class, int.class, int.class)
                .newInstance(columns, rows, bugs);
        PrintStream console = System.out;
        System.setOut(new PrintStream(OutputStream.nullOutputStream())); // jar logs infections
        Object game = gameClass.getConstructor(paramsClass).newInstance(params);
        try {
            Field openField = gameClass.getDeclaredField("openState");
            openField.setAccessible(true);
            Object open = openField.get(game);
            Method addNewPlayer = open.getClass().getMethod("addNewPlayer", playerClass);
            for (int i = 0; i < programs.size(); i++) {
                // Player's constructor opens sockets, so create it without running it.
                Object player = allocateInstance.invoke(unsafe, playerClass);
                playerClass.getMethod("setProgram", List.class)
                        .invoke(player, Arrays.stream(programs.get(i)).boxed().toList());
                playerClass.getMethod("setSpeciesID", int.class).invoke(player, i);
                addNewPlayer.invoke(open, player);
            }
            Field runningField = gameClass.getDeclaredField("runningState");
            runningField.setAccessible(true);
            Object running = runningField.get(game);
            Method oneStep = running.getClass().getMethod("oneStep");
            for (int t = 0; t < ticks; t++) {
                oneStep.invoke(running);
            }
            int[] counts = new int[programs.size()];
            Method getSpecies = bugClass.getMethod("getSpecies");
            for (Object b : (List<?>) gameClass.getMethod("getBugs").invoke(game)) {
                counts[(Integer) getSpecies.invoke(b)]++;
            }
            return counts;
        } finally {
            System.setOut(console);
            ((ServerSocket) gameClass.getMethod("getListenSocket").invoke(game)).close();
        }
    }

    private static double mean(double[] xs) {
        return Arrays.stream(xs).average().orElse(0);
    }

    private static double variance(double[] xs) {
        double m = mean(xs);
        return Arrays.stream(xs).map(x -> (x - m) * (x - m)).sum() / (xs.length - 1);
    }
}
