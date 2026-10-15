package edu.osu.bugsworld.engine.sim;

/**
 * Draws a snapshot as text: each cell is two characters, the species id and
 * an arrow for the facing ({@code 0^ 1> 2v 3<}), or {@code " ."} if empty.
 */
public final class AsciiRenderer {

    private AsciiRenderer() {
    }

    public static String render(Snapshot s) {
        String[][] cells = new String[s.rows()][s.columns()];
        for (BugView b : s.bugs()) {
            cells[b.row()][b.column()] = String.valueOf(b.species()) + arrow(b.facing());
        }
        StringBuilder out = new StringBuilder();
        String border = "#".repeat(s.columns() * 2 + 2);
        out.append(border).append('\n');
        for (String[] row : cells) {
            out.append('#');
            for (String cell : row) {
                out.append(cell == null ? " ." : cell);
            }
            out.append("#\n");
        }
        out.append(border).append('\n');
        out.append("tick ").append(s.tick());
        for (SpeciesStats st : s.species()) {
            out.append("  ").append(st.species()).append('=').append(st.name()).append(':')
                    .append(st.current());
        }
        return out.append('\n').toString();
    }

    private static char arrow(Facing f) {
        return switch (f) {
            case NORTH -> '^';
            case EAST -> '>';
            case SOUTH -> 'v';
            case WEST -> '<';
        };
    }
}
