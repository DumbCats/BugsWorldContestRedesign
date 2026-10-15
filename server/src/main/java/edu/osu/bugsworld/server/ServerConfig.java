package edu.osu.bugsworld.server;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.SecureRandom;
import java.util.Map;

/**
 * Server settings, read from environment variables.
 *
 * @param port            HTTP port ({@code PORT}, default 7070)
 * @param dataDir         where the SQLite database lives ({@code BUGSWORLD_DATA_DIR}, default
 *                        {@code BugsWorld} in the user's home folder, so it doesn't depend on
 *                        where the start script was launched from and survives upgrades)
 * @param instructorToken required for {@code /api/admin} ({@code BUGSWORLD_INSTRUCTOR_TOKEN};
 *                        if unset, generated once and kept in the data folder)
 * @param classCode       if set, students need it to submit ({@code BUGSWORLD_CLASS_CODE})
 */
public record ServerConfig(int port, Path dataDir, String instructorToken, String classCode) {

    public static final int MAX_SOURCE_LENGTH = 100_000;
    public static final int MAX_NAME_LENGTH = 60;

    /** Where a generated instructor token is kept, so it survives restarts. */
    static Path tokenFile(Path dataDir) {
        return dataDir.resolve("instructor-token.txt");
    }

    static ServerConfig fromEnvironment(Map<String, String> env) {
        String token = env.get("BUGSWORLD_INSTRUCTOR_TOKEN");
        String classCode = env.get("BUGSWORLD_CLASS_CODE");
        Path dataDir = env.containsKey("BUGSWORLD_DATA_DIR") ? Path.of(env.get("BUGSWORLD_DATA_DIR"))
                : Path.of(System.getProperty("user.home"), "BugsWorld");
        return new ServerConfig(
                Integer.parseInt(env.getOrDefault("PORT", "7070")),
                dataDir,
                token == null || token.isBlank() ? savedOrNewToken(dataDir) : token.trim(),
                classCode == null || classCode.isBlank() ? null : classCode.trim());
    }

    /** The token saved in the data folder, or a new one (saved for next time). */
    static String savedOrNewToken(Path dataDir) {
        Path file = tokenFile(dataDir);
        try {
            if (Files.exists(file)) {
                String saved = Files.readString(file).strip();
                if (!saved.isEmpty()) {
                    return saved;
                }
            }
            String token = randomToken();
            Files.createDirectories(dataDir);
            Files.writeString(file, token + "\n");
            return token;
        } catch (IOException e) {
            return randomToken(); // read-only folder: still usable, just not remembered
        }
    }

    /** Eight easy-to-type characters (no 0/O or 1/l/I). */
    static String randomToken() {
        String alphabet = "abcdefghjkmnpqrstuvwxyz23456789";
        SecureRandom random = new SecureRandom();
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < 8; i++) {
            sb.append(alphabet.charAt(random.nextInt(alphabet.length())));
        }
        return sb.toString();
    }
}
