package edu.osu.bugsworld.engine.sim;

/** The direction a bug faces. Row numbers grow southward. */
public enum Facing {
    NORTH(-1, 0),
    EAST(0, 1),
    SOUTH(1, 0),
    WEST(0, -1);

    private static final Facing[] VALUES = values();

    private final int rowDelta;
    private final int columnDelta;

    Facing(int rowDelta, int columnDelta) {
        this.rowDelta = rowDelta;
        this.columnDelta = columnDelta;
    }

    public int rowDelta() {
        return rowDelta;
    }

    public int columnDelta() {
        return columnDelta;
    }

    /** 90 degrees counter-clockwise. */
    public Facing left() {
        return VALUES[(ordinal() + 3) % 4];
    }

    /** 90 degrees clockwise. */
    public Facing right() {
        return VALUES[(ordinal() + 1) % 4];
    }
}
