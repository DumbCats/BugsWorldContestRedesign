package edu.osu.bugsworld.engine.compiler;

import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * The OSU reference compiler ({@code components.program.Program1} from
 * lib/BWClient.jar), called through its public API via reflection so the
 * build doesn't depend on the jar.
 */
final class ReferenceCompiler implements AutoCloseable {

    private final URLClassLoader loader;
    private final Class<?> program1;
    private final Class<?> reader1L;
    private final Method parse;
    private final Method generatedCode;
    private final Method closeReader;

    ReferenceCompiler(Path jar) throws ReflectiveOperationException, IOException {
        loader = new URLClassLoader(new URL[] {jar.toUri().toURL()}, null);
        program1 = loader.loadClass("components.program.Program1");
        reader1L = loader.loadClass("components.simplereader.SimpleReader1L");
        Class<?> reader = loader.loadClass("components.simplereader.SimpleReader");
        parse = program1.getMethod("parse", reader);
        generatedCode = program1.getMethod("generatedCode");
        closeReader = reader.getMethod("close");
    }

    /** The reference object code, or empty if the reference rejects the source. */
    Optional<int[]> compile(String source) throws Exception {
        Path file = Files.createTempFile("bugsworld-oracle", ".bl");
        try {
            Files.writeString(file, source);
            Object reader = reader1L.getConstructor(String.class).newInstance(file.toString());
            try {
                Object program = program1.getConstructor().newInstance();
                parse.invoke(program, reader);
                List<Integer> code = new ArrayList<>();
                for (Object x : (Iterable<?>) generatedCode.invoke(program)) {
                    code.add((Integer) x);
                }
                return Optional.of(code.stream().mapToInt(Integer::intValue).toArray());
            } catch (InvocationTargetException e) {
                // Reporter.assertElseFatalError throws RuntimeException("Error: ...").
                if (e.getCause() instanceof RuntimeException r && r.getMessage() != null
                        && r.getMessage().startsWith("Error:")) {
                    return Optional.empty();
                }
                throw e;
            } finally {
                closeReader.invoke(reader);
            }
        } finally {
            Files.deleteIfExists(file);
        }
    }

    @Override
    public void close() throws IOException {
        loader.close();
    }
}
