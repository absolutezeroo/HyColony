package dev.hycolony.core.farming.hut;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.hycolony.core.farming.field.FarmField;
import dev.hycolony.core.kernel.BlockPos;
import dev.hycolony.core.kernel.Directions;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/** The distance and short direction of a Fields tab row (MC "Distance: 5m N/E"), north being -z. */
class FieldsTabTest {
    private static final BlockPos HUT = new BlockPos(0, 64, 0);

    @Test
    void directionsFollowTheCompass() {
        assertEquals("n", Directions.of(HUT, new BlockPos(0, 64, -10)));
        assertEquals("ne", Directions.of(HUT, new BlockPos(7, 64, -7)));
        assertEquals("e", Directions.of(HUT, new BlockPos(10, 64, 0)));
        assertEquals("s", Directions.of(HUT, new BlockPos(0, 64, 10)));
        assertEquals("w", Directions.of(HUT, new BlockPos(-10, 64, 1)));
        assertEquals("nw", Directions.of(HUT, new BlockPos(-7, 64, -7)));
    }

    @Test
    void sectorBoundsTruncateTheAngleAsMc() {
        // atan2 gives 22.78 and 67.22 degrees; MC truncates to 22 (north) and 67 (west).
        assertEquals("n", Directions.of(HUT, new BlockPos(-42, 64, -100)));
        assertEquals("w", Directions.of(HUT, new BlockPos(-100, 64, -42)));
        assertEquals("sw", Directions.of(HUT, new BlockPos(-7, 64, 7)));
        assertEquals("se", Directions.of(HUT, new BlockPos(7, 64, 7)));
        assertEquals("w", Directions.of(HUT, new BlockPos(-100, 64, 41)), "112.3 degrees");
        assertEquals("s", Directions.of(HUT, new BlockPos(-42, 64, 100)), "157.2 degrees");
        assertEquals("e", Directions.of(HUT, new BlockPos(100, 64, 43)), "-113.3 truncates to -113, so 247");
        assertEquals("ne", Directions.of(HUT, new BlockPos(100, 64, -42)), "-67.2 truncates to -67, so 293");
        assertEquals("ne", Directions.of(HUT, new BlockPos(43, 64, -100)), "-23.3 truncates to 337");
        assertEquals("n", Directions.of(HUT, new BlockPos(42, 64, -100)), "-22.8 truncates to -22, so 338");
    }

    @Test
    void sameColumnIsAboveBelowOrSameAsMc() {
        assertEquals("up", Directions.of(HUT, new BlockPos(0, 70, 0)));
        assertEquals("down", Directions.of(HUT, new BlockPos(0, 60, 0)));
        assertEquals("same", Directions.of(HUT, HUT));
    }

    @Test
    void distanceIsTheWholeEuclideanDistance() {
        assertEquals(5, FieldsTab.distance(HUT, new BlockPos(3, 64, 4)));
    }

    @Test
    void fieldWorkedTodayIsMarkedUntilTheNextDay() {
        FarmerColony c = new FarmerColony(1);
        FarmField f = c.field(3, true);
        c.fields().assign(c.colony, c.hut, f);
        c.setDay(1);
        c.fields().fieldToWorkOn(c.colony, c.hut);
        c.fields().resetCurrentField(c.colony); // its pass is over

        assertTrue(row(c).doneToday(), "back tomorrow");
        c.setDay(2);
        assertFalse(row(c).doneToday());
    }

    private static FieldsView.Row row(FarmerColony c) {
        FieldsView view = (FieldsView) c.fields().tab(c.colony, c.hut, UUID.randomUUID());
        return view.rows().getFirst();
    }
}
