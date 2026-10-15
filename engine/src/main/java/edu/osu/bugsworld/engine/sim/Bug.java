package edu.osu.bugsworld.engine.sim;

/** A bug's mutable state. Owned by its {@link Game}; never handed out. */
final class Bug {

    final int id;
    Species species;
    int row;
    int column;
    Facing facing;
    int pc;

    Bug(int id, Species species, int row, int column, Facing facing) {
        this.id = id;
        this.species = species;
        this.row = row;
        this.column = column;
        this.facing = facing;
    }

    BugView view() {
        return new BugView(id, species.id(), row, column, facing, pc);
    }
}
