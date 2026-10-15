package edu.osu.bugsworld.engine;

import edu.osu.bugsworld.engine.compiler.CompileResult;
import edu.osu.bugsworld.engine.compiler.Compiler;
import edu.osu.bugsworld.engine.compiler.Diagnostic;
import edu.osu.bugsworld.engine.sim.AsciiRenderer;
import edu.osu.bugsworld.engine.sim.Game;
import edu.osu.bugsworld.engine.sim.GameConfig;
import edu.osu.bugsworld.engine.sim.Snapshot;
import edu.osu.bugsworld.engine.sim.SpeciesStats;
import edu.osu.bugsworld.engine.vm.BugProgram;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * Command-line simulator: compiles .bl (or loads .bo) files and runs games
 * in the terminal.
 *
 * <pre>
 * ./gradlew -q :engine:simulate --args="samples/hunter.bl samples/wanderer.bl"
 * options: --rows N --cols N --bugs N --ticks N --seed N
 *          --show N   draw the world every N ticks (0 = only at the end)
 *          --runs N   play N games (seeds seed, seed+1, ...) and summarize
 * </pre>
 */
public final class Simulate {

    private Simulate() {
    }

    private record Entry(String name, BugProgram program) {
    }

    public static void main(String[] args) throws IOException {
        GameConfig config = GameConfig.defaults(1);
        long ticks = 500;
        int show = 0;
        int runs = 1;
        List<Entry> entries = new ArrayList<>();
        for (int i = 0; i < args.length; i++) {
            switch (args[i]) {
                case "--rows" -> config = config.withSize(Integer.parseInt(args[++i]), config.columns());
                case "--cols" -> config = config.withSize(config.rows(), Integer.parseInt(args[++i]));
                case "--bugs" -> config = config.withBugsPerSpecies(Integer.parseInt(args[++i]));
                case "--seed" -> config = config.withSeed(Long.parseLong(args[++i]));
                case "--ticks" -> ticks = Long.parseLong(args[++i]);
                case "--show" -> show = Integer.parseInt(args[++i]);
                case "--runs" -> runs = Integer.parseInt(args[++i]);
                default -> {
                    Entry e = load(Path.of(args[i]));
                    if (e == null) {
                        System.exit(1);
                    }
                    entries.add(e);
                }
            }
        }
        if (entries.isEmpty()) {
            System.err.println("usage: simulate [options] bug.bl [bug2.bl ...]");
            System.exit(2);
        }
        config = config.withMaxTicks(ticks);
        if (runs == 1) {
            playOne(config, entries, show);
        } else {
            playMany(config, entries, runs);
        }
    }

    private static Entry load(Path file) throws IOException {
        String text = Files.readString(file);
        String name = file.getFileName().toString().replaceFirst("\\.(bl|bo)$", "");
        if (file.toString().endsWith(".bo")) {
            return new Entry(name, BugProgram.of(ObjectCode.parseBo(text)));
        }
        CompileResult r = Compiler.compile(text);
        for (Diagnostic d : r.diagnostics()) {
            System.err.println(file + ":" + d);
        }
        return r.succeeded() ? new Entry(name, BugProgram.of(r.code())) : null;
    }

    private static Game newGame(GameConfig config, List<Entry> entries) {
        Game game = new Game(config);
        for (Entry e : entries) {
            game.addSpecies(e.name(), e.program());
        }
        return game;
    }

    private static void playOne(GameConfig config, List<Entry> entries, int show) {
        Game game = newGame(config, entries);
        Snapshot s = game.snapshot();
        System.out.print(AsciiRenderer.render(s));
        while (s.phase() != Game.Phase.ENDED) {
            s = game.step();
            if (show > 0 && s.tick() % show == 0) {
                System.out.print(AsciiRenderer.render(s));
            }
        }
        if (show == 0 || s.tick() % show != 0) {
            System.out.print(AsciiRenderer.render(s));
        }
        for (SpeciesStats st : s.species()) {
            System.out.printf("%d %-12s current %3d  min %3d  max %3d  stuck %d  halted %d%n",
                    st.species(), st.name(), st.current(), st.min(), st.max(), st.stuck(),
                    st.halted());
        }
    }

    private static void playMany(GameConfig config, List<Entry> entries, int runs) {
        int[] wins = new int[entries.size()];
        double[] totals = new double[entries.size()];
        for (int r = 0; r < runs; r++) {
            Game game = newGame(config.withSeed(config.seed() + r), entries);
            Snapshot s = game.snapshot();
            while (s.phase() != Game.Phase.ENDED) {
                s = game.step();
            }
            int best = -1;
            int bestCount = -1;
            for (SpeciesStats st : s.species()) {
                totals[st.species()] += st.current();
                if (st.current() > bestCount) {
                    best = st.species();
                    bestCount = st.current();
                }
            }
            wins[best]++;
        }
        System.out.printf("%d games, %d ticks each, seeds %d..%d%n", runs, config.maxTicks(),
                config.seed(), config.seed() + runs - 1);
        for (int i = 0; i < entries.size(); i++) {
            System.out.printf("%d %-12s most bugs in %4d games   average final count %.1f%n", i,
                    entries.get(i).name(), wins[i], totals[i] / runs);
        }
    }
}
