package edu.osu.bugsworld.engine.compiler;

import java.util.Collection;
import java.util.Optional;

/** "Did you mean ...?" suggestions. */
final class Names {

    private Names() {
    }

    /**
     * The candidate closest to {@code name}, if any is close enough to be a
     * plausible typo. A case-only difference always counts.
     */
    static Optional<String> closest(String name, Collection<String> candidates) {
        String best = null;
        int bestDistance = Integer.MAX_VALUE;
        for (String c : candidates) {
            if (c.equalsIgnoreCase(name)) {
                return Optional.of(c);
            }
            int d = distance(name.toLowerCase(), c.toLowerCase());
            if (d < bestDistance) {
                best = c;
                bestDistance = d;
            }
        }
        int allowed = name.length() <= 3 ? 1 : name.length() <= 6 ? 2 : 3;
        return bestDistance <= allowed ? Optional.of(best) : Optional.empty();
    }

    /** Levenshtein distance, counting a swap of adjacent chars as one edit. */
    static int distance(String a, String b) {
        int[][] d = new int[a.length() + 1][b.length() + 1];
        for (int i = 0; i <= a.length(); i++) {
            d[i][0] = i;
        }
        for (int j = 0; j <= b.length(); j++) {
            d[0][j] = j;
        }
        for (int i = 1; i <= a.length(); i++) {
            for (int j = 1; j <= b.length(); j++) {
                int cost = a.charAt(i - 1) == b.charAt(j - 1) ? 0 : 1;
                d[i][j] = Math.min(Math.min(d[i - 1][j] + 1, d[i][j - 1] + 1), d[i - 1][j - 1] + cost);
                if (i > 1 && j > 1 && a.charAt(i - 1) == b.charAt(j - 2)
                        && a.charAt(i - 2) == b.charAt(j - 1)) {
                    d[i][j] = Math.min(d[i][j], d[i - 2][j - 2] + 1);
                }
            }
        }
        return d[a.length()][b.length()];
    }
}
