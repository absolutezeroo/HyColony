package dev.hycolony.core.farming.hut;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.JsonObject;
import dev.hycolony.core.farming.field.FarmField;
import java.util.Optional;
import org.junit.jupiter.api.Test;

/** MC BuildingFarmer.FarmerFieldsModule: how many fields a farmer hut owns and how it claims and frees them. */
class FarmerFieldsModuleTest {
    @Test
    void levelOneHutOwnsOneFieldAtMost() {
        FarmerColony c = new FarmerColony(1);
        FarmField a = c.field(10, true);
        FarmField b = c.field(20, true);

        assertTrue(c.fields().assign(c.colony, c.hut, a));
        assertFalse(c.fields().assign(c.colony, c.hut, b));
        assertEquals(Optional.of(FarmerColony.HUT), a.owner());
        assertTrue(b.owner().isEmpty());
    }

    @Test
    void seedlessFieldIsNeverAssigned() {
        FarmerColony c = new FarmerColony(5);
        FarmField a = c.field(10, false);

        assertFalse(c.fields().assign(c.colony, c.hut, a));
        c.fields().onColonyTick(c.colony, c.hut);

        assertFalse(a.isTaken());
    }

    @Test
    void autoClaimTakesOneFreeFieldPerTick() {
        FarmerColony c = new FarmerColony(5);
        FarmField a = c.field(10, true);
        FarmField b = c.field(20, true);

        c.fields().onColonyTick(c.colony, c.hut);
        assertTrue(a.isTaken());
        assertFalse(b.isTaken());

        c.fields().onColonyTick(c.colony, c.hut);
        assertTrue(b.isTaken());
    }

    @Test
    void manualModeClaimsNothing() {
        FarmerColony c = new FarmerColony(5);
        FarmField a = c.field(10, true);
        c.fields().setAssignManually(true);

        c.fields().onColonyTick(c.colony, c.hut);

        assertFalse(a.isTaken());
    }

    @Test
    void levelZeroHutClaimsNothing() {
        FarmerColony c = new FarmerColony(0);
        FarmField a = c.field(10, true);

        c.fields().onColonyTick(c.colony, c.hut);

        assertFalse(a.isTaken());
    }

    @Test
    void freeingReleasesTheField() {
        FarmerColony c = new FarmerColony(1);
        FarmField a = c.field(10, true);
        c.fields().assign(c.colony, c.hut, a);

        c.fields().free(c.colony, c.hut, a);

        assertFalse(a.isTaken());
    }

    @Test
    void removingTheHutFreesItsFields() {
        FarmerColony c = new FarmerColony(2);
        FarmField a = c.field(10, true);
        c.fields().assign(c.colony, c.hut, a);

        c.colony.buildings().remove(FarmerColony.HUT);
        c.fields().onRemoved(c.colony, c.hut);

        assertFalse(a.isTaken());
    }

    @Test
    void modeSurvivesSaveAndLoad() {
        FarmerColony c = new FarmerColony(1);
        c.fields().setAssignManually(true);
        JsonObject saved = new JsonObject();
        c.fields().write(saved);

        FarmerFieldsModule back = new FarmerFieldsModule();
        back.read(saved);

        assertTrue(back.assignManually());
    }
}
