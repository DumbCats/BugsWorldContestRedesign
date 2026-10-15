package edu.osu.bugsworld.server;

import edu.osu.bugsworld.engine.sim.BugView;
import edu.osu.bugsworld.engine.sim.Game;
import edu.osu.bugsworld.engine.sim.GameConfig;
import edu.osu.bugsworld.engine.sim.Snapshot;
import edu.osu.bugsworld.engine.vm.BugProgram;
import java.util.ArrayList;
import java.util.List;

/**
 * A short practice game a student runs from the editor page: their bug
 * against one example bug. The whole game is simulated at once and returned
 * as frames for the browser to animate, so it doesn't touch the class game.
 */
final class Practice {

    static final int ROWS = 12;
    static final int COLUMNS = 12;
    static final int BUGS = 15;
    static final int TICKS = 300;

    /**
     * @param species names, by species id
     * @param frames  frame 0 is the start, then one per tick; each frame lists
     *                four numbers per bug (species, row, column, facing), bugs
     *                always in the same order
     * @param result  stats at the end
     */
    record Run(long seed, int rows, int columns, List<String> species, List<int[]> frames,
            GameView result) {
    }

    private Practice() {
    }

    static Run run(BugProgram mine, String opponentName, BugProgram opponent, long seed) {
        Game game = new Game(GameConfig.defaults(seed).withSize(ROWS, COLUMNS)
                .withBugsPerSpecies(BUGS).withMaxTicks(TICKS));
        List<String> names = new ArrayList<>();
        names.add(game.addSpecies("Your bug", mine).name());
        if (opponent != null) {
            names.add(game.addSpecies(opponentName, opponent).name());
        }
        List<int[]> frames = new ArrayList<>();
        Snapshot s = game.snapshot();
        frames.add(frame(s));
        while (s.phase() != Game.Phase.ENDED) {
            s = game.step();
            frames.add(frame(s));
        }
        GameView result = GameView.of(0, "ended", s, game.config(), 0, java.util.Map.of())
                .withoutBugs();
        return new Run(seed, ROWS, COLUMNS, names, frames, result);
    }

    private static int[] frame(Snapshot s) {
        int[] f = new int[s.bugs().size() * 4];
        int i = 0;
        for (BugView b : s.bugs()) {
            f[i++] = b.species();
            f[i++] = b.row();
            f[i++] = b.column();
            f[i++] = b.facing().ordinal();
        }
        return f;
    }
}
