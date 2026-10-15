package edu.osu.bugsworld.engine;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.util.List;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestFactory;

class ObjectCodeTest {

    @Test
    void opcodeNumberingMatchesJavadoc() {
        assertEquals(0, Opcode.MOVE.code());
        assertEquals(5, Opcode.HALT.code());
        assertEquals(6, Opcode.JUMP.code());
        assertEquals(7, Opcode.JUMP_IF_NOT_NEXT_IS_EMPTY.code());
        assertEquals(16, Opcode.JUMP_IF_NOT_TRUE.code());
        assertEquals(17, Opcode.values().length);
        assertTrue(Opcode.HALT.isPrimitive());
        assertFalse(Opcode.JUMP.isPrimitive());
    }

    @Test
    void parsesBoFormat() {
        ObjectCode code = ObjectCode.parseBo("6\n16\n5\n4\n6\n0\n5\n");
        assertArrayEquals(new int[] {16, 5, 4, 6, 0, 5}, code.toArray());
        assertEquals("6\n16\n5\n4\n6\n0\n5\n", code.toBo());
    }

    @Test
    void rejectsInvalidCode() {
        assertThrows(IllegalArgumentException.class, ObjectCode::of);
        assertThrows(IllegalArgumentException.class, () -> ObjectCode.of(17));
        assertThrows(IllegalArgumentException.class, () -> ObjectCode.of(6));
        assertThrows(IllegalArgumentException.class, () -> ObjectCode.of(6, 2));
        assertThrows(IllegalArgumentException.class, () -> ObjectCode.of(7, -1, 5));
        assertThrows(IllegalArgumentException.class, () -> ObjectCode.of(6, 1, 5)); // into operand
        ObjectCode.of(7, 3, 0, 5);
        assertThrows(IllegalArgumentException.class, () -> ObjectCode.parseBo("3\n0\n5\n"));
        assertThrows(IllegalArgumentException.class, () -> ObjectCode.parseBo("2\n0\nx\n"));
    }

    @TestFactory
    List<DynamicTest> referenceOutputIsValidAndEndsWithHalt() throws IOException {
        return Golden.valid().stream()
                .map(c -> DynamicTest.dynamicTest(c.name(), () -> {
                    ObjectCode code = ObjectCode.parseBo(c.expectedBo());
                    assertEquals(Opcode.HALT.code(), code.get(code.length() - 1));
                    assertEquals(c.expectedBo(), code.toBo());
                }))
                .toList();
    }
}
