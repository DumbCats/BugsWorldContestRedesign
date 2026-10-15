import components.program.Program;
import components.program.Program1;
import components.simplereader.SimpleReader;
import components.simplereader.SimpleReader1L;

import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Compiles BL files with the OSU reference compiler bundled in lib/BWClient.jar
 * (components.program.Program1) and writes the expected output next to each
 * input, for use as golden test data.
 *
 * <p>For {@code foo.bl}: on success writes {@code foo.bo} in the format that
 * BWClient loads (first line is the length, then one integer per line); on
 * failure writes {@code foo.err} containing the reference error message.
 *
 * <p>Run with tools/oracle/regen-golden.sh. Not part of the Gradle build,
 * because the OSU jars are not redistributable and are not committed.
 */
public final class ReferenceCompile {

    private ReferenceCompile() {
    }

    public static void main(String[] args) throws IOException {
        for (String arg : args) {
            Path source = Path.of(arg);
            String base = source.toString().replaceFirst("\\.bl$", "");
            Path bo = Path.of(base + ".bo");
            Path err = Path.of(base + ".err");
            Files.deleteIfExists(bo);
            Files.deleteIfExists(err);
            try {
                Files.writeString(bo, compile(source));
                System.out.println("ok    " + source);
            } catch (RuntimeException e) {
                // Program1 reports BL errors as RuntimeException("Error: ...").
                Files.writeString(err, e.getMessage() + "\n");
                System.out.println("error " + source + ": " + e.getMessage());
            }
        }
    }

    private static String compile(Path source) {
        SimpleReader in = new SimpleReader1L(source.toString());
        try {
            Program p = new Program1();
            p.parse(in);
            StringWriter text = new StringWriter();
            PrintWriter out = new PrintWriter(text);
            int length = p.generatedCode().length();
            out.println(length);
            for (int x : p.generatedCode()) {
                out.println(x);
            }
            out.flush();
            return text.toString();
        } finally {
            in.close();
        }
    }
}
