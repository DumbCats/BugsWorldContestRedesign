package edu.osu.bugsworld.engine.sim;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import edu.osu.bugsworld.engine.compiler.CompileResult;
import edu.osu.bugsworld.engine.compiler.Compiler;
import edu.osu.bugsworld.engine.sim.Game.Placement;
import edu.osu.bugsworld.engine.vm.BugProgram;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

class GameTest {

    private static final String FOREVER_MOVE = "WHILE true DO move END WHILE";
    private static final String FOREVER_INFECT = "WHILE true DO infect END WHILE";
    private static final String FOREVER_SKIP = "WHILE true DO skip END WHILE";

    private static BugProgram program(String body) {
        CompileResult r = Compiler.compile("PROGRAM T IS BEGIN " + body + " END T");
        assertTrue(r.succeeded(), () -> r.errors().toString());
        return BugProgram.of(r.code());
    }

    private static GameConfig world(int rows, int columns) {
        return GameConfig.defaults(1).withSize(rows, columns).withBugsPerSpecies(1);
    }

    private static Placement at(int row, int column, Facing facing) {
        return new Placement(row, column, facing);
    }

    private static BugView bug(Snapshot s, int id) {
        return s.bugs().stream().filter(b -> b.id() == id).findFirst().orElseThrow();
    }

    // ------------------------------------------------------------ placement

    @Test
    void speciesArePlacedOnDistinctRandomCells() {
        Game game = new Game(GameConfig.defaults(1));
        for (int i = 0; i < 3; i++) {
            game.addSpecies("s" + i, program(FOREVER_SKIP));
        }
        Snapshot s = game.snapshot();
        assertEquals(150, s.bugs().size());
        Set<Integer> cells = new HashSet<>();
        Set<Facing> facings = EnumSet.noneOf(Facing.class);
        for (BugView b : s.bugs()) {
            assertTrue(b.row() >= 0 && b.row() < 15 && b.column() >= 0 && b.column() < 15);
            assertTrue(cells.add(b.row() * 15 + b.column()), "two bugs on one cell");
            facings.add(b.facing());
        }
        assertEquals(EnumSet.allOf(Facing.class), facings);
        s.species().forEach(st -> assertEquals(50, st.current()));
    }

    @Test
    void tooLittleRoomIsAClearError() {
        Game game = new Game(GameConfig.defaults(1).withSize(3, 3).withBugsPerSpecies(5));
        game.addSpecies("a", program(FOREVER_SKIP));
        IllegalStateException e = assertThrows(IllegalStateException.class,
                () -> game.addSpecies("b", program(FOREVER_SKIP)));
        assertTrue(e.getMessage().contains("4 empty cells"), e.getMessage());
    }

    @Test
    void atMostTenSpecies() {
        Game game = new Game(world(10, 10));
        for (int i = 0; i < 10; i++) {
            assertEquals(i, game.addSpecies("s" + i, program(FOREVER_SKIP)).id());
        }
        assertThrows(IllegalStateException.class, () -> game.addSpecies("x", program(FOREVER_SKIP)));
    }

    @Test
    void invalidSettingsAreRejected() {
        assertThrows(IllegalArgumentException.class, () -> GameConfig.defaults(1).withSize(0, 5));
        assertThrows(IllegalArgumentException.class, () -> GameConfig.defaults(1).withSize(101, 5));
        assertThrows(IllegalArgumentException.class,
                () -> GameConfig.defaults(1).withBugsPerSpecies(301));
    }

    @Test
    void speciesCanBeRemovedOnlyBeforeTheStart() {
        Game game = new Game(world(1, 2).withBugsPerSpecies(1));
        Species a = game.addSpecies("a", program(FOREVER_SKIP));
        game.addSpecies("b", program(FOREVER_SKIP));
        game.removeSpecies(a.id());
        assertEquals(1, game.snapshot().bugs().size());
        assertEquals(0, game.addSpecies("c", program(FOREVER_SKIP)).id(), "id 0 is free again");
        game.step();
        assertThrows(IllegalStateException.class, () -> game.removeSpecies(0));
        assertThrows(IllegalStateException.class, () -> game.addSpecies("d", program(FOREVER_SKIP)));
    }

    // ---------------------------------------------------------- determinism

    @Test
    void sameSeedReplaysExactly() {
        assertEquals(play(42), play(42));
        assertNotEquals(play(42), play(43));
    }

    private static List<Snapshot> play(long seed) {
        Game game = new Game(GameConfig.defaults(seed));
        game.addSpecies("hunter", program(
                "WHILE true DO IF next-is-enemy THEN infect ELSE IF next-is-empty THEN move "
                        + "ELSE IF random THEN turnleft ELSE turnright END IF END IF END IF END WHILE"));
        game.addSpecies("drifter", program(
                "WHILE true DO IF random THEN move ELSE turnleft END IF END WHILE"));
        return java.util.stream.Stream.generate(game::step).limit(200).toList();
    }

    // -------------------------------------------------------------- actions

    @Test
    void wallsSurroundTheWorld() {
        Game game = new Game(world(1, 1));
        game.addSpecies("a", program("WHILE true DO IF next-is-wall THEN turnright ELSE move END IF END WHILE"),
                List.of(at(0, 0, Facing.NORTH)));
        Snapshot s = game.step();
        assertEquals(List.of(new Event.Turned(0, Facing.EAST)), s.events());
        assertEquals(0, bug(s, 0).row());
    }

    @Test
    void moveOnlyIntoAnEmptyCell() {
        // Two bugs race for the middle cell; whoever goes first wins it.
        for (long seed = 0; seed < 20; seed++) {
            Game game = new Game(world(1, 3).withSeed(seed));
            game.addSpecies("a", program(FOREVER_MOVE), List.of(at(0, 0, Facing.EAST)));
            game.addSpecies("b", program(FOREVER_MOVE), List.of(at(0, 2, Facing.WEST)));
            Snapshot s = game.step();
            assertEquals(1, s.events().size(), s.events().toString());
            Event.Moved m = (Event.Moved) s.events().getFirst();
            assertEquals(1, m.column());
            assertNotEquals(bug(s, 0).column(), bug(s, 1).column());
        }
    }

    @Test
    void infectionConvertsTheBugInFront() {
        Game game = new Game(world(1, 2));
        Species a = game.addSpecies("a", program(FOREVER_INFECT), List.of(at(0, 0, Facing.EAST)));
        game.addSpecies("b", program(FOREVER_SKIP), List.of(at(0, 1, Facing.SOUTH)));
        Snapshot s = game.step();
        assertEquals(new Event.Infected(0, 1, 1, a.id()), s.events().getFirst());
        BugView victim = bug(s, 1);
        assertEquals(a.id(), victim.species());
        assertEquals(1, victim.column());
        assertEquals(Facing.SOUTH, victim.facing(), "keeps its facing");
    }

    @Test
    void cannotInfectAFriend() {
        Game game = new Game(world(1, 2).withBugsPerSpecies(2));
        game.addSpecies("a", program(FOREVER_INFECT),
                List.of(at(0, 0, Facing.EAST), at(0, 1, Facing.WEST)));
        assertEquals(List.of(), game.step().events());
    }

    @Test
    void infectedBugTakesItsTurnAsTheNewSpeciesByDefault() {
        // B's own program reaches pc 1 after one turn; A's program from pc 0
        // reaches pc 3. Infection resets pc to 0. So after one tick B's pc is
        // 0 if it acted before being infected, 3 if it acted after.
        String bProgram = "skip skip skip " + FOREVER_SKIP;
        assertEquals(Set.of(0, 3), victimPcsAfterOneTick(true, bProgram));
        assertEquals(Set.of(0), victimPcsAfterOneTick(false, bProgram));
    }

    private static Set<Integer> victimPcsAfterOneTick(boolean sameTick, String bProgram) {
        Set<Integer> pcs = new HashSet<>();
        for (long seed = 0; seed < 64; seed++) {
            Game game = new Game(world(1, 2).withSeed(seed).withInfectedBugsActSameTick(sameTick));
            game.addSpecies("a", program(FOREVER_INFECT), List.of(at(0, 0, Facing.EAST)));
            game.addSpecies("b", program(bProgram), List.of(at(0, 1, Facing.SOUTH)));
            pcs.add(bug(game.step(), 1).pc());
        }
        return pcs;
    }

    // ----------------------------------------------------- jar defects fixed

    @Test
    void haltedBugsJustStop() {
        Game game = new Game(world(3, 3).withBugsPerSpecies(2));
        game.addSpecies("short", program("move"));
        for (int i = 0; i < 5; i++) {
            game.step();
        }
        Snapshot s = game.snapshot();
        assertEquals(5, s.tick());
        assertEquals(2, s.species().getFirst().halted());
    }

    @Test
    void stuckBugsLoseTheirTurnInsteadOfFreezingTheGame() {
        Game game = new Game(world(3, 3).withBugsPerSpecies(2));
        game.addSpecies("spinner", program("WHILE true DO END WHILE"));
        game.addSpecies("mover", program(FOREVER_MOVE));
        Snapshot s = game.step();
        assertEquals(2, s.species().get(0).stuck());
        assertEquals(0, s.species().get(1).stuck());
    }

    // ------------------------------------------------------ stats and ending

    @Test
    void statsTrackMinAndMax() {
        Game game = new Game(world(1, 2));
        game.addSpecies("a", program(FOREVER_INFECT), List.of(at(0, 0, Facing.EAST)));
        game.addSpecies("b", program(FOREVER_SKIP), List.of(at(0, 1, Facing.WEST)));
        Snapshot s = game.step();
        assertEquals(new SpeciesStats(0, "a", 2, 1, 2, 0, 0, 0), s.species().get(0));
        assertEquals(new SpeciesStats(1, "b", 0, 0, 1, 0, 0, 0), s.species().get(1));
    }

    @Test
    void endsAfterTheTickLimit() {
        Game game = new Game(world(2, 2).withMaxTicks(3));
        game.addSpecies("a", program(FOREVER_SKIP));
        game.step();
        game.step();
        Snapshot s = game.step();
        assertEquals(Game.Phase.ENDED, s.phase());
        assertEquals(Game.EndReason.TICK_LIMIT, s.endReason());
        assertThrows(IllegalStateException.class, game::step);
    }

    @Test
    void canEndWhenOneSpeciesIsLeft() {
        Game game = new Game(world(1, 2).withEndWhenOneSpeciesLeft(true));
        game.addSpecies("a", program(FOREVER_INFECT), List.of(at(0, 0, Facing.EAST)));
        game.addSpecies("b", program(FOREVER_SKIP), List.of(at(0, 1, Facing.WEST)));
        assertEquals(Game.EndReason.ONE_SPECIES_LEFT, game.step().endReason());
    }

    @Test
    void runsUntilEndedByDefault() {
        Game game = new Game(world(1, 2));
        game.addSpecies("a", program(FOREVER_INFECT), List.of(at(0, 0, Facing.EAST)));
        game.addSpecies("b", program(FOREVER_SKIP), List.of(at(0, 1, Facing.WEST)));
        for (int i = 0; i < 10; i++) {
            assertEquals(Game.Phase.RUNNING, game.step().phase());
        }
        game.end();
        assertEquals(Game.EndReason.MANUAL, game.snapshot().endReason());
    }

    @Test
    void snapshotsDoNotChangeLater() {
        Game game = new Game(world(1, 3));
        game.addSpecies("a", program(FOREVER_MOVE), List.of(at(0, 0, Facing.EAST)));
        Snapshot before = game.snapshot();
        game.step();
        assertEquals(0, bug(before, 0).column());
        assertThrows(UnsupportedOperationException.class, () -> before.bugs().clear());
    }

    @Test
    void asciiRendering() {
        Game game = new Game(world(2, 3));
        game.addSpecies("a", program(FOREVER_SKIP), List.of(at(0, 1, Facing.EAST)));
        game.addSpecies("b", program(FOREVER_SKIP), List.of(at(1, 2, Facing.SOUTH)));
        assertEquals("""
                ########
                # .0> .#
                # . .1v#
                ########
                tick 0  0=a:1  1=b:1
                """, AsciiRenderer.render(game.snapshot()));
    }
}
