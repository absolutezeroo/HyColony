package dev.hycolony.core.farming.hut;

import static org.junit.jupiter.api.Assertions.assertEquals;

import dev.hycolony.core.kernel.BlockPos;
import org.junit.jupiter.api.Test;

/** The distance and short direction of a Fields tab row (MC "Distance: 5m NE"), north being -z. */
class FieldsTabTest {
    private static final BlockPos HUT = new BlockPos(0, 64, 0);

    @Test
    void directionsFollowTheCompass() {
        assertEquals("n", FieldsTab.direction(HUT, new BlockPos(0, 64, -10)));
        assertEquals("ne", FieldsTab.direction(HUT, new BlockPos(7, 64, -7)));
        assertEquals("e", FieldsTab.direction(HUT, new BlockPos(10, 64, 0)));
        assertEquals("s", FieldsTab.direction(HUT, new BlockPos(0, 64, 10)));
        assertEquals("w", FieldsTab.direction(HUT, new BlockPos(-10, 64, 1)));
        assertEquals("nw", FieldsTab.direction(HUT, new BlockPos(-7, 64, -7)));
    }

    @Test
    void distanceIsTheWholeEuclideanDistance() {
        assertEquals(5, FieldsTab.distance(HUT, new BlockPos(3, 64, 4)));
    }
}
