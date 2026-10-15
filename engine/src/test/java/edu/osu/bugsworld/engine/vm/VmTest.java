package edu.osu.bugsworld.engine.vm;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import edu.osu.bugsworld.engine.ObjectCode;
import edu.osu.bugsworld.engine.Opcode;
import edu.osu.bugsworld.engine.compiler.CompileResult;
import edu.osu.bugsworld.engine.compiler.Compiler;
import edu.osu.bugsworld.engine.compiler.Condition;
import java.util.ArrayList;
import java.util.List;
import java.util.SplittableRandom;
import java.util.random.RandomGenerator;
import org.junit.jupiter.api.Test;

class VmTest {

    private static BugProgram compile(String body) {
        CompileResult r = Compiler.compile("PROGRAM T IS BEGIN " + body + " END T");
        assertTrue(r.succeeded(), () -> r.errors().toString());
        return BugProgram.of(r.code());
    }

    private static final RandomGenerator NO_RANDOM = new RandomGenerator() {
        @Override
        public long nextLong() {
            throw new AssertionError("random was not expected");
        }
    };

    /** A generator whose nextBoolean always returns {@code value}, counting calls. */
    private static final class FixedCoin implements RandomGenerator {
        private final boolean value;
        int draws;

        FixedCoin(boolean value) {
            this.value = value;
        }

        @Override
        public boolean nextBoolean() {
            draws++;
            return value;
        }

        @Override
        public long nextLong() {
            throw new AssertionError("only nextBoolean is used");
        }
    }

    @Test
    void primitivesAdvanceByOne() {
        BugProgram p = compile("move turnleft turnright infect skip");
        List<Action> expected = List.of(Action.MOVE, Action.TURN_LEFT, Action.TURN_RIGHT,
                Action.INFECT, Action.SKIP);
        int pc = 0;
        for (int i = 0; i < expected.size(); i++) {
            Step s = Vm.step(p, pc, CellState.EMPTY, NO_RANDOM);
            assertEquals(new Step(expected.get(i), i + 1, 0), s);
            pc = s.nextPc();
        }
    }

    @Test
    void haltedBugStaysHalted() {
        BugProgram p = compile("move");
        Step first = Vm.step(p, 0, CellState.EMPTY, NO_RANDOM);
        assertEquals(Action.MOVE, first.action());
        for (int i = 0; i < 3; i++) {
            assertEquals(new Step(Action.HALTED, 1, 0),
                    Vm.step(p, first.nextPc(), CellState.ENEMY, NO_RANDOM));
        }
    }

    @Test
    void runningOffTheEndOfUploadedCodeHalts() {
        BugProgram p = BugProgram.of(ObjectCode.of(Opcode.MOVE.code()));
        assertEquals(Action.MOVE, Vm.step(p, 0, CellState.EMPTY, NO_RANDOM).action());
        assertEquals(new Step(Action.HALTED, 1, 0), Vm.step(p, 1, CellState.EMPTY, NO_RANDOM));
    }

    @Test
    void conditionalJumpsJumpWhenTheConditionIsFalse() {
        for (Condition c : Condition.values()) {
            if (c == Condition.RANDOM) {
                continue;
            }
            // IF c THEN move ELSE turnleft END IF
            BugProgram p = compile("IF " + c.text() + " THEN move ELSE turnleft END IF");
            for (CellState sees : CellState.values()) {
                boolean holds = switch (c) {
                    case NEXT_IS_EMPTY -> sees == CellState.EMPTY;
                    case NEXT_IS_NOT_EMPTY -> sees != CellState.EMPTY;
                    case NEXT_IS_WALL -> sees == CellState.WALL;
                    case NEXT_IS_NOT_WALL -> sees != CellState.WALL;
                    case NEXT_IS_FRIEND -> sees == CellState.FRIEND;
                    case NEXT_IS_NOT_FRIEND -> sees != CellState.FRIEND;
                    case NEXT_IS_ENEMY -> sees == CellState.ENEMY;
                    case NEXT_IS_NOT_ENEMY -> sees != CellState.ENEMY;
                    case TRUE -> true;
                    case RANDOM -> throw new AssertionError();
                };
                Step s = Vm.step(p, 0, sees, NO_RANDOM);
                assertEquals(holds ? Action.MOVE : Action.TURN_LEFT, s.action(), c + " / " + sees);
                // The conditional jump executes on both paths (taken or not).
                assertEquals(1, s.jumps(), c + " / " + sees);
            }
        }
    }

    @Test
    void randomDrawsOneBooleanPerEvaluation() {
        BugProgram p = compile("IF random THEN move ELSE turnleft END IF");
        FixedCoin heads = new FixedCoin(true);
        assertEquals(Action.MOVE, Vm.step(p, 0, CellState.EMPTY, heads).action());
        assertEquals(1, heads.draws);
        FixedCoin tails = new FixedCoin(false);
        assertEquals(Action.TURN_LEFT, Vm.step(p, 0, CellState.EMPTY, tails).action());
        assertEquals(1, tails.draws);
    }

    @Test
    void loopsResumeAfterTheirAction() {
        BugProgram p = compile("WHILE true DO move turnleft END WHILE");
        int pc = 0;
        List<Action> actions = new ArrayList<>();
        for (int i = 0; i < 6; i++) {
            Step s = Vm.step(p, pc, CellState.WALL, NO_RANDOM);
            actions.add(s.action());
            pc = s.nextPc();
        }
        assertEquals(List.of(Action.MOVE, Action.TURN_LEFT, Action.MOVE, Action.TURN_LEFT,
                Action.MOVE, Action.TURN_LEFT), actions);
    }

    @Test
    void emptyLoopIsStuckImmediately() {
        BugProgram p = compile("WHILE true DO END WHILE");
        for (CellState sees : CellState.values()) {
            assertEquals(new Step(Action.STUCK, 0, 0), Vm.step(p, 0, sees, NO_RANDOM));
        }
    }

    @Test
    void stuckDependsOnWhatTheBugSees() {
        // Froze the jar whenever the bug faced anything but an enemy.
        BugProgram p = compile("WHILE true DO IF next-is-enemy THEN infect END IF END WHILE");
        Step stuck = Vm.step(p, 0, CellState.FRIEND, NO_RANDOM);
        assertEquals(Action.STUCK, stuck.action());
        Step later = Vm.step(p, stuck.nextPc(), CellState.ENEMY, NO_RANDOM);
        assertEquals(Action.INFECT, later.action());
    }

    @Test
    void loopThatNeverActsWhateverRandomSaysIsStuck() {
        BugProgram p = compile("WHILE true DO IF random THEN ELSE END IF END WHILE");
        assertEquals(Action.STUCK, Vm.step(p, 0, CellState.EMPTY, NO_RANDOM).action());
    }

    @Test
    void randomLoopThatCanExitIsNotStuck() {
        BugProgram p = compile("WHILE random DO END WHILE move");
        // Two heads keep looping, then tails exits to move.
        RandomGenerator coins = new RandomGenerator() {
            private int calls;

            @Override
            public boolean nextBoolean() {
                return calls++ < 2;
            }

            @Override
            public long nextLong() {
                throw new AssertionError();
            }
        };
        Step s = Vm.step(p, 0, CellState.EMPTY, coins);
        assertEquals(Action.MOVE, s.action());
        assertEquals(5, s.jumps()); // test, back-jump, test, back-jump, test-and-exit
    }

    @Test
    void jumpBudgetStopsEndlessRandomLoopsAndResumes() {
        BugProgram p = compile("WHILE random DO END WHILE move");
        FixedCoin heads = new FixedCoin(true);
        Step s = Vm.step(p, 0, CellState.EMPTY, heads, 100);
        assertEquals(Action.OUT_OF_JUMPS, s.action());
        assertEquals(100, s.jumps());
        Step next = Vm.step(p, s.nextPc(), CellState.EMPTY, new FixedCoin(false), 100);
        assertEquals(Action.MOVE, next.action());
    }

    @Test
    void seededRunsAreReproducible() {
        BugProgram p = compile(
                "WHILE true DO IF random THEN move ELSE IF random THEN turnleft ELSE skip END IF END IF END WHILE");
        assertEquals(run(p, 42), run(p, 42));
        assertTrue(!run(p, 42).equals(run(p, 43)));
    }

    private static List<Action> run(BugProgram p, long seed) {
        SplittableRandom rng = new SplittableRandom(seed);
        List<Action> actions = new ArrayList<>();
        int pc = 0;
        for (int i = 0; i < 50; i++) {
            Step s = Vm.step(p, pc, CellState.EMPTY, rng);
            actions.add(s.action());
            pc = s.nextPc();
        }
        return actions;
    }
}
