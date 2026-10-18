package edu.osu.bugsworld.server;

import java.awt.GraphicsEnvironment;
import java.lang.reflect.InvocationTargetException;
import javax.swing.JOptionPane;

/**
 * Entry point of the downloadable app. Compiled for Java 8 so it can run on
 * an old Java and explain what to install, instead of the JVM's unreadable
 * "UnsupportedClassVersionError". Then it starts {@link Main}.
 */
public final class Launcher {

    static final int REQUIRED = 21;

    private Launcher() {
    }

    public static void main(String[] args) throws Throwable {
        int version = majorVersion(System.getProperty("java.specification.version", "0"));
        if (version < REQUIRED) {
            String message = "BugsWorld needs Java " + REQUIRED + " or newer, but this computer is using Java "
                    + version + ".\n\nInstall a current Java (free, e.g. from https://adoptium.net), "
                    + "then start BugsWorld again.\nJava in use: " + System.getProperty("java.home");
            System.err.println(message);
            if (!GraphicsEnvironment.isHeadless()) {
                JOptionPane.showMessageDialog(null, message, "BugsWorld", JOptionPane.WARNING_MESSAGE);
            }
            System.exit(1);
        }
        try {
            Class.forName("edu.osu.bugsworld.server.Main")
                    .getMethod("main", String[].class)
                    .invoke(null, (Object) args);
        } catch (InvocationTargetException e) {
            throw e.getCause();
        }
    }

    /** "1.8" means 8; "17" means 17. */
    static int majorVersion(String specification) {
        String s = specification.startsWith("1.") ? specification.substring(2) : specification;
        int end = 0;
        while (end < s.length() && Character.isDigit(s.charAt(end))) {
            end++;
        }
        return end == 0 ? 0 : Integer.parseInt(s.substring(0, end));
    }
}
