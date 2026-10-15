package edu.osu.bugsworld.engine.compiler;

/** Explains why a token is not a valid BL identifier. */
record InvalidName(String reason, String hint) {

    static InvalidName explain(String text) {
        char first = text.charAt(0);
        if (!isAsciiLetter(first)) {
            return new InvalidName("names must start with a letter", null);
        }
        if (text.indexOf('_') >= 0) {
            return new InvalidName("names can't contain '_'",
                    "Use '-' instead, e.g. " + text.replace('_', '-'));
        }
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if (!isAsciiLetter(c) && !(c >= '0' && c <= '9') && c != '-') {
                if (Character.isWhitespace(c) || Character.isSpaceChar(c) || Character.isISOControl(c)) {
                    return new InvalidName(String.format(
                            "it contains an invisible character (U+%04X)", (int) c),
                            "Retype the spaces around it");
                }
                return new InvalidName("names may contain only letters, digits and '-', not '" + c + "'",
                        null);
            }
        }
        return new InvalidName("names may contain only letters, digits and '-'", null);
    }

    private static boolean isAsciiLetter(char c) {
        return (c >= 'a' && c <= 'z') || (c >= 'A' && c <= 'Z');
    }
}
