package edu.osu.bugsworld.server;

import java.awt.Desktop;
import java.net.BindException;
import java.net.URI;
import java.util.Locale;
import java.util.concurrent.TimeUnit;

/** Starts the BugsWorld server, prints how to reach it, and opens the dashboard. */
public final class Main {

    private Main() {
    }

    public static void main(String[] args) {
        ServerConfig config = ServerConfig.fromEnvironment(System.getenv());
        String local = "http://localhost:" + config.port();
        Server server;
        try {
            server = Server.start(config);
        } catch (RuntimeException e) {
            if (causedBy(e, BindException.class)) {
                System.err.println();
                System.err.println("Port " + config.port() + " is already in use, so BugsWorld can't start.");
                System.err.println("It is probably already running: open " + local + "/admin");
                System.err.println("To run a second copy, set PORT to another number (e.g. PORT=7071).");
                System.exit(1);
            }
            throw e;
        }
        Runtime.getRuntime().addShutdownHook(new Thread(server::close));

        System.out.println();
        System.out.println("BugsWorld is running. It keeps running until you close this window");
        System.out.println("or press Ctrl+C. (Started with Gradle, it shows EXECUTING meanwhile.)");
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
            String link = local + "/admin#token=" + config.instructorToken();
            if (openBrowser(link)) {
                System.out.println("Opened the dashboard in your browser.");
            } else {
                System.out.println("Couldn't open a browser. Open this link to sign in to the dashboard:");
                System.out.println("  " + link);
            }
        }
    }

    /** Tries Java's desktop support, then the operating system's own opener. */
    private static boolean openBrowser(String url) {
        try {
            if (Desktop.isDesktopSupported()
                    && Desktop.getDesktop().isSupported(Desktop.Action.BROWSE)) {
                Desktop.getDesktop().browse(URI.create(url));
                return true;
            }
        } catch (Exception | Error e) {
            // fall through: on Linux (especially Wayland) Java's support often fails
        }
        String os = System.getProperty("os.name", "").toLowerCase(Locale.ROOT);
        String[] command = os.contains("win")
                ? new String[] {"rundll32", "url.dll,FileProtocolHandler", url}
                : os.contains("mac") ? new String[] {"open", url}
                : new String[] {"xdg-open", url};
        try {
            Process p = new ProcessBuilder(command)
                    .redirectErrorStream(true)
                    .redirectOutput(ProcessBuilder.Redirect.DISCARD)
                    .start();
            // Openers usually return at once; if one lingers, assume it worked.
            return !p.waitFor(10, TimeUnit.SECONDS) || p.exitValue() == 0;
        } catch (Exception e) {
            return false;
        }
    }

    private static boolean causedBy(Throwable t, Class<? extends Throwable> type) {
        for (Throwable c = t; c != null; c = c.getCause()) {
            if (type.isInstance(c)) {
                return true;
            }
        }
        return false;
    }
}
