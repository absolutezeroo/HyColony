package dev.hycolony.core.farming.hut;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.JsonObject;
import dev.hycolony.core.farming.field.FarmField;
import java.util.Optional;
import org.junit.jupiter.api.Test;

/** MC BuildingExtensionsModule.getExtensionToWorkOn and resetCurrentExtension: which field the farmer works next. */
class FieldChoiceTest {
    @Test
    void currentFieldIsKeptWhileItExists() {
        FarmerColony c = new FarmerColony(2);
        FarmField a = owned(c, 10);
        owned(c, 20);

        assertEquals(Optional.of(a), c.fields().fieldToWorkOn(c.colony, c.hut));
        assertEquals(Optional.of(a), c.fields().fieldToWorkOn(c.colony, c.hut));
    }

    @Test
    void neverCheckedFieldComesFirst() {
        FarmerColony c = new FarmerColony(2);
        FarmField a = owned(c, 10);
        FarmField b = owned(c, 20);

        c.fields().fieldToWorkOn(c.colony, c.hut);
        c.fields().resetCurrentField(c.colony);

        assertEquals(Optional.of(b), c.fields().fieldToWorkOn(c.colony, c.hut));
        assertTrue(a.isTaken());
    }

    @Test
    void fieldDoneTodayIsNotPickedAgainUntilTomorrow() {
        FarmerColony c = new FarmerColony(1);
        FarmField a = owned(c, 10);
        c.setDay(3);

        c.fields().fieldToWorkOn(c.colony, c.hut);
        c.fields().resetCurrentField(c.colony);

        assertTrue(c.fields().fieldToWorkOn(c.colony, c.hut).isEmpty());
        c.setDay(4);
        assertEquals(Optional.of(a), c.fields().fieldToWorkOn(c.colony, c.hut));
    }

    @Test
    void oldestCheckedFieldIsPickedNext() {
        FarmerColony c = new FarmerColony(2);
        FarmField a = owned(c, 10);
        FarmField b = owned(c, 20);
        c.setDay(1);
        c.fields().fieldToWorkOn(c.colony, c.hut); // a
        c.fields().resetCurrentField(c.colony);
        c.setDay(2);
        c.fields().fieldToWorkOn(c.colony, c.hut); // b, never checked
        c.fields().resetCurrentField(c.colony);
        c.setDay(3);

        assertEquals(Optional.of(a), c.fields().fieldToWorkOn(c.colony, c.hut));
        assertTrue(b.isTaken());
    }

    @Test
    void checkedDaysSurviveSaveAndLoad() {
        FarmerColony c = new FarmerColony(1);
        owned(c, 10);
        c.setDay(3);
        c.fields().fieldToWorkOn(c.colony, c.hut);
        c.fields().resetCurrentField(c.colony);
        JsonObject saved = new JsonObject();
        c.fields().write(saved);

        FarmerFieldsModule back = new FarmerFieldsModule();
        back.read(saved);

        assertTrue(back.fieldToWorkOn(c.colony, c.hut).isEmpty(), "still done for day 3 after a reload");
    }

    private static FarmField owned(FarmerColony c, int x) {
        FarmField f = c.field(x, true);
        f.setOwner(Optional.of(FarmerColony.HUT));
        return f;
    }
}
