package dev.hycolony.core.farming.hut;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.hycolony.core.colony.ui.tab.FieldsView;
import dev.hycolony.core.farming.field.FarmField;
import dev.hycolony.core.farming.field.FieldRadii;
import dev.hycolony.core.kernel.BlockPos;
import java.util.UUID;
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

    @Test
    void rowCarriesTheFieldRadiiToHighlightIt() {
        FarmerColony c = new FarmerColony(1);
        FarmField f = c.field(3, true);
        f.setRadii(new FieldRadii(2, 3, 4, 1));

        assertEquals(new FieldRadii(2, 3, 4, 1), row(c).radii());
    }
}
