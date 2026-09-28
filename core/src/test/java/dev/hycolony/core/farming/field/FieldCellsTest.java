package dev.hycolony.core.farming.field;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.HashSet;
import java.util.OptionalInt;
import java.util.Set;
import org.junit.jupiter.api.Test;

/** MC EntityAIWorkFarmer.nextValidCell: an outward square spiral around the field block, bounded by its radii. */
class FieldCellsTest {
    @Test
    void firstRingFollowsMcOrder() {
        int[][] expected = {{1, 1}, {0, 1}, {-1, 1}, {-1, 0}, {-1, -1}, {0, -1}, {1, -1}, {1, 0}};
        for (int cell = 0; cell < expected.length; cell++) {
            assertArrayEquals(expected[cell], FieldCells.offset(cell), "cell " + cell);
        }
    }

    @Test
    void defaultFieldVisitsEveryCellOnceButTheCentre() {
        Set<String> seen = visit(FieldRadii.defaults());
        assertEquals(11 * 11 - 1, seen.size());
    }

    @Test
    void spiralNeverLeavesTheField() {
        for (FieldRadii r : new FieldRadii[] {new FieldRadii(0, 0, 20, 0), new FieldRadii(1, 1, 1, 1)}) {
            int cell = -1;
            int visited = 0;
            OptionalInt next;
            while ((next = FieldCells.next(cell, r)).isPresent()) {
                cell = next.getAsInt();
                int[] o = FieldCells.offset(cell);
                assertTrue(-o[1] <= r.north() && o[0] <= r.east() && o[1] <= r.south() && -o[0] <= r.west());
                visited++;
            }
            assertEquals((r.west() + r.east() + 1) * (r.north() + r.south() + 1) - 1, visited);
        }
    }

    private static Set<String> visit(FieldRadii r) {
        Set<String> seen = new HashSet<>();
        int cell = -1;
        OptionalInt next;
        while ((next = FieldCells.next(cell, r)).isPresent()) {
            cell = next.getAsInt();
            int[] o = FieldCells.offset(cell);
            assertTrue(seen.add(o[0] + "," + o[1]), "visited twice: " + o[0] + "," + o[1]);
        }
        return seen;
    }
}
