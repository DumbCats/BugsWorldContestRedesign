package edu.osu.bugsworld.engine.vm;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import edu.osu.bugsworld.engine.compiler.CompileResult;
import edu.osu.bugsworld.engine.compiler.Compiler;
import edu.osu.bugsworld.engine.compiler.RandomPrograms;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Random;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/**
 * Differential test of our VM against the jar's {@code bugsworld.Bug}: for
 * random programs and random sequences of what the bug sees, both must choose
 * the same actions.
 *
 * <p>Programs using {@code random} are skipped (the jar's coin is unseeded).
 * A sequence stops when our VM reports HALTED (the jar crashes on the turn
 * after HALT) or STUCK (the jar would loop forever).
 */
class VmReferenceTest {

    private static URLClassLoader loader;
    private static Object unsafe;
    private static Method allocateInstance;

    @BeforeAll
    static void load() throws Exception {
        String jar = System.getProperty("bugsworld.referenceJar", "");
        assumeTrue(!jar.isEmpty() && Files.exists(Path.of(jar)), "reference jar not available");
        loader = new URLClassLoader(new URL[] {Path.of(jar).toUri().toURL()}, null);
        // Player's constructor opens sockets, so create it without running it.
        Class<?> unsafeClass = Class.forName("sun.misc.Unsafe");
        Field theUnsafe = unsafeClass.getDeclaredField("theUnsafe");
        theUnsafe.setAccessible(true);
        unsafe = theUnsafe.get(null);
        allocateInstance = unsafeClass.getMethod("allocateInstance", Class.class);
    }

    @AfterAll
    static void close() throws Exception {
        if (loader != null) {
            loader.close();
        }
    }

    /** One jar bug, driven through its public methods. */
    private static final class JarBug {
        private final Object bug;
        private final Method setSee;
        private final Method getNextPrimitive;
        private final Class<?> canSee;

        JarBug(List<Integer> program) throws Exception {
            Class<?> playerClass = loader.loadClass("bugsworld.Player");
            Class<?> bugClass = loader.loadClass("bugsworld.Bug");
            Class<?> facing = loader.loadClass("bugsworld.Bug$Facing");
            Class<?> cellClass = loader.loadClass("bugsworld.World$Cell");
            canSee = loader.loadClass("bugsworld.Bug$CanSee");
            Object player = allocateInstance.invoke(unsafe, playerClass);
            playerClass.getMethod("setProgram", List.class).invoke(player, program);
            Object cell = cellClass.getConstructor(int.class, int.class, boolean.class)
                    .newInstance(1, 1, false);
            Object north = facing.getField("NORTH").get(null);
            bug = bugClass.getConstructor(facing, cellClass, playerClass)
                    .newInstance(north, cell, player);
            setSee = bugClass.getMethod("setSee", canSee);
            getNextPrimitive = bugClass.getMethod("getNextPrimitive");
        }

        String next(CellState sees) throws Exception {
            setSee.invoke(bug, canSee.getField(sees.name()).get(null));
            return ((Enum<?>) getNextPrimitive.invoke(bug)).name();
        }
    }

    private static final Map<Action, String> JAR_NAMES = Map.of(
            Action.MOVE, "MOVE", Action.TURN_LEFT, "TURNLEFT", Action.TURN_RIGHT, "TURNRIGHT",
            Action.INFECT, "INFECT", Action.SKIP, "SKIP", Action.HALTED, "HALT");

    @Test
    void choosesTheSameActionsAsTheJar() throws Exception {
        RandomPrograms programs = new RandomPrograms(99);
        Random world = new Random(7);
        CellState[] views = CellState.values();
        int compared = 0;
        int sequences = 0;
        while (sequences < 5000) {
            String source = programs.program();
            if (source.contains("random")) {
                continue;
            }
            CompileResult r = Compiler.compile(source);
            assertTrue(r.succeeded(), source);
            sequences++;
            BugProgram ours = BugProgram.of(r.code());
            JarBug jar = new JarBug(Arrays.stream(r.code().toArray()).boxed().toList());
            int pc = 0;
            for (int turn = 0; turn < 40; turn++) {
                CellState sees = views[world.nextInt(views.length)];
                Step s = Vm.step(ours, pc, sees, null);
                if (s.action() == Action.STUCK) {
                    break;
                }
                String where = "turn " + turn + " seeing " + sees + " in:\n" + source;
                assertEquals(JAR_NAMES.get(s.action()), jar.next(sees), where);
                compared++;
                if (s.action() == Action.HALTED) {
                    break;
                }
                pc = s.nextPc();
            }
        }
        assertTrue(compared > 10_000, "compared only " + compared);
    }
}
