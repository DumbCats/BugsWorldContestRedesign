package edu.osu.bugsworld.server;

import java.nio.file.Path;
import java.security.SecureRandom;
import java.util.Map;

/**
 * Server settings, read from environment variables.
 *
 * @param port            HTTP port ({@code PORT}, default 7070)
 * @param dataDir         where the SQLite database lives ({@code BUGSWORLD_DATA_DIR}, default {@code data})
 * @param instructorToken required for {@code /api/admin} ({@code BUGSWORLD_INSTRUCTOR_TOKEN};
 *                        generated and printed at startup if unset)
 * @param classCode       if set, students need it to submit ({@code BUGSWORLD_CLASS_CODE})
 */
public record ServerConfig(int port, Path dataDir, String instructorToken, String classCode) {

    public static final int MAX_SOURCE_LENGTH = 100_000;
    public static final int MAX_NAME_LENGTH = 60;

    static ServerConfig fromEnvironment(Map<String, String> env) {
        String token = env.get("BUGSWORLD_INSTRUCTOR_TOKEN");
        String classCode = env.get("BUGSWORLD_CLASS_CODE");
        return new ServerConfig(
                Integer.parseInt(env.getOrDefault("PORT", "7070")),
                Path.of(env.getOrDefault("BUGSWORLD_DATA_DIR", "data")),
                token == null || token.isBlank() ? randomToken() : token.trim(),
                classCode == null || classCode.isBlank() ? null : classCode.trim());
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
