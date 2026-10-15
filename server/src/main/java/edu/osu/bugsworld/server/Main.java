package edu.osu.bugsworld.server;

import java.awt.Desktop;
import java.net.URI;

/** Starts the BugsWorld server, prints how to reach it, and opens the dashboard. */
public final class Main {

    private Main() {
    }

    public static void main(String[] args) {
        ServerConfig config = ServerConfig.fromEnvironment(System.getenv());
        Server server = Server.start(config);
        Runtime.getRuntime().addShutdownHook(new Thread(server::close));
        String local = "http://localhost:" + server.port();

        System.out.println();
        System.out.println("BugsWorld is running. Leave this window open during the lab.");
        System.out.println();
        System.out.println("  Professor dashboard:  " + local + "/admin");
        System.out.println("  Instructor token:     " + config.instructorToken()
                + (System.getenv("BUGSWORLD_INSTRUCTOR_TOKEN") == null
                        ? "   (kept in " + ServerConfig.tokenFile(config.dataDir()) + ")" : ""));
        for (String url : Network.studentUrls(server.port())) {
            System.out.println("  Students open:        " + url);
        }
        System.out.println("  Class code:           " + (config.classCode() == null
                ? "none (set BUGSWORLD_CLASS_CODE to require one)" : config.classCode()));
        System.out.println("  Data folder:          " + config.dataDir().toAbsolutePath());
        System.out.println();

        if (!"false".equalsIgnoreCase(System.getenv("BUGSWORLD_OPEN_BROWSER"))) {
            // The token rides in the URL fragment, which never leaves the
            // browser; the dashboard stores it and removes it from the address.
            openBrowser(local + "/admin#token=" + config.instructorToken());
        }
    }

    private static void openBrowser(String url) {
        try {
            if (Desktop.isDesktopSupported()
                    && Desktop.getDesktop().isSupported(Desktop.Action.BROWSE)) {
                Desktop.getDesktop().browse(URI.create(url));
            }
        } catch (Exception | Error e) {
            // No desktop (e.g. a headless server): the printed URLs are enough.
        }
    }
}
