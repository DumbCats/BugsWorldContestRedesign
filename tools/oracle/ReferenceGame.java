import bugsworld.Bug;
import bugsworld.Game;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/**
 * Runs the real BugsWorld server engine from lib/BWServer.jar headlessly and
 * prints per-species bug counts over time. Each program joins through the
 * same socket handshake BWClient uses.
 *
 * <p>Usage: ReferenceGame rows cols bugsPerPlayer seconds "prog1" "prog2" ...
 * where each prog is comma-separated object code, e.g. "16, 5, 1, 6, 0, 5".
 *
 * <p>The jar engine is not seeded, so compare against our engine
 * statistically (many runs), never tick for tick.
 */
public final class ReferenceGame {

    private ReferenceGame() {
    }

    private static void join(Game game, String program) throws Exception {
        Socket toServer = new Socket("localhost", Integer.parseInt(game.getPort()));
        try (ServerSocket reverse = new ServerSocket(0)) {
            PrintWriter w = new PrintWriter(toServer.getOutputStream(), true);
            w.print("localhost\n" + reverse.getLocalPort() + "\ntrue\n");
            w.flush();
            Socket fromServer = reverse.accept();
            BufferedReader r = new BufferedReader(
                    new InputStreamReader(fromServer.getInputStream()));
            String id = r.readLine();
            w.println(program);
            System.err.println("joined as species " + id + ": " + r.readLine());
        }
    }

    private static String counts(Game game) {
        Map<Integer, Integer> bySpecies = new TreeMap<>();
        List<String> cells = new ArrayList<>();
        for (Bug b : game.getBugs()) {
            bySpecies.merge(b.getSpecies(), 1, Integer::sum);
            cells.add(b.getCell().getRow() + "," + b.getCell().getCol() + b.getFacing());
        }
        Collections.sort(cells);
        return bySpecies + " stateHash=" + cells.hashCode();
    }

    public static void main(String[] args) throws Exception {
        int rows = Integer.parseInt(args[0]);
        int cols = Integer.parseInt(args[1]);
        int bugs = Integer.parseInt(args[2]);
        int seconds = Integer.parseInt(args[3]);
        // GameParams takes (columns, rows, bugsPerPlayer).
        Game game = new Game(new Game.GameParams(cols, rows, bugs));
        game.open();
        Thread.sleep(300);
        for (int i = 4; i < args.length; i++) {
            join(game, args[i]);
        }
        Thread.sleep(1200); // OpenState polls accept() every 500 ms
        game.close();
        Thread.sleep(300);
        game.start();
        for (int i = 0; i < seconds * 2; i++) {
            Thread.sleep(500);
            System.out.println("t=" + game.getTime() + "ms " + counts(game));
        }
        System.exit(0);
    }
}
