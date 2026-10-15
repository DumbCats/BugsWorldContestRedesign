package edu.osu.bugsworld.engine.compiler;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.BooleanSupplier;

/**
 * Warnings about how a correct program will behave, and the code-size limit.
 * Runs only on error-free programs.
 *
 * <p>Both warnings flag situations that froze the whole game in the jar
 * server (docs/reference-behavior.md §10). In our engine the bug only loses
 * turns, but the student should still know.
 */
final class Analysis {

    private final Map<String, Ast.Instruction> table;
    private final Map<String, Boolean> finishesSilently = new HashMap<>();
    private final Map<String, Boolean> finishes = new HashMap<>();
    private final Map<String, Long> sizes = new HashMap<>();

    private Analysis(Map<String, Ast.Instruction> table) {
        this.table = table;
    }

    static void run(Ast.Program program, Map<String, Ast.Instruction> table, Diagnostics diags,
            int maxCodeLength) {
        Analysis a = new Analysis(table);
        long size = a.size(program.body()) + 1;
        if (size > maxCodeLength) {
            diags.error("code-too-large", program.nameSpan(),
                    "This program is too large: with every instruction call expanded it has "
                            + size + " numbers of object code (the limit is " + maxCodeLength + ")",
                    "Each call copies the instruction's whole body, so avoid calling big "
                            + "instructions from many places");
            return;
        }
        a.silentLoops(program.body(), new HashSet<>(), diags);
        if (a.canFinish(program.body()) && program.endSpan() != null) {
            diags.warning("program-can-halt", program.endSpan(),
                    "The program can reach its end, and then the bug does nothing for the "
                            + "rest of the game",
                    "Wrap the main statements in WHILE true DO ... END WHILE");
        }
    }

    /** Warns about each reachable WHILE whose body can finish a pass without acting. */
    private void silentLoops(List<Ast.Stmt> block, Set<String> visited, Diagnostics diags) {
        for (Ast.Stmt s : block) {
            switch (s) {
                case Ast.Call c -> {
                    Ast.Instruction target = table.get(c.name());
                    if (target != null && visited.add(c.name())) {
                        silentLoops(target.body(), visited, diags);
                    }
                }
                case Ast.If i -> silentLoops(i.thenBlock(), visited, diags);
                case Ast.IfElse i -> {
                    silentLoops(i.thenBlock(), visited, diags);
                    silentLoops(i.elseBlock(), visited, diags);
                }
                case Ast.While w -> {
                    if (canFinishSilently(w.body())) {
                        diags.warning("silent-loop", w.span(),
                                "This loop can repeat without the bug doing anything: some path "
                                        + "through it has no move, turnleft, turnright, infect "
                                        + "or skip. When that happens the bug loses its turn.",
                                "Make every path through the loop do at least one action, "
                                        + "e.g. add ELSE skip");
                    }
                    silentLoops(w.body(), visited, diags);
                }
            }
        }
    }

    /** Can this block run from start to end without executing a primitive? */
    private boolean canFinishSilently(List<Ast.Stmt> block) {
        for (Ast.Stmt s : block) {
            boolean silent = switch (s) {
                case Ast.Call c -> {
                    Ast.Instruction target = table.get(c.name());
                    yield target != null && memo(finishesSilently, c.name(),
                            () -> canFinishSilently(target.body()));
                }
                case Ast.If i -> i.condition() != Condition.TRUE || canFinishSilently(i.thenBlock());
                case Ast.IfElse i -> i.condition() == Condition.TRUE
                        ? canFinishSilently(i.thenBlock())
                        : canFinishSilently(i.thenBlock()) || canFinishSilently(i.elseBlock());
                case Ast.While w -> w.condition() != Condition.TRUE;
            };
            if (!silent) {
                return false;
            }
        }
        return true;
    }

    /** Can this block ever run to its end (e.g. it is not stuck in WHILE true)? */
    private boolean canFinish(List<Ast.Stmt> block) {
        for (Ast.Stmt s : block) {
            boolean ends = switch (s) {
                case Ast.Call c -> {
                    Ast.Instruction target = table.get(c.name());
                    yield target == null || memo(finishes, c.name(),
                            () -> canFinish(target.body()));
                }
                case Ast.If i -> i.condition() != Condition.TRUE || canFinish(i.thenBlock());
                case Ast.IfElse i -> i.condition() == Condition.TRUE
                        ? canFinish(i.thenBlock())
                        : canFinish(i.thenBlock()) || canFinish(i.elseBlock());
                case Ast.While w -> w.condition() != Condition.TRUE;
            };
            if (!ends) {
                return false;
            }
        }
        return true;
    }

    /** Like computeIfAbsent, but allows {@code compute} to fill in other keys. */
    private static boolean memo(Map<String, Boolean> cache, String key, BooleanSupplier compute) {
        Boolean known = cache.get(key);
        if (known == null) {
            known = compute.getAsBoolean();
            cache.put(key, known);
        }
        return known;
    }

    /** Object code length of a block, with calls expanded. Saturates instead of overflowing. */
    private long size(List<Ast.Stmt> block) {
        long total = 0;
        for (Ast.Stmt s : block) {
            long n = switch (s) {
                case Ast.Call c -> {
                    Ast.Instruction target = table.get(c.name());
                    if (target == null) {
                        yield 1;
                    }
                    Long known = sizes.get(c.name());
                    if (known == null) {
                        known = size(target.body());
                        sizes.put(c.name(), known);
                    }
                    yield known;
                }
                case Ast.If i -> 2 + size(i.thenBlock());
                case Ast.IfElse i -> 4 + size(i.thenBlock()) + size(i.elseBlock());
                case Ast.While w -> 4 + size(w.body());
            };
            total = Math.min(Long.MAX_VALUE / 4, total + n);
        }
        return total;
    }
}
