package dev.hycolony.core.farming.hut;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import dev.hycolony.core.farming.field.FarmField;
import dev.hycolony.core.farming.field.FieldRadii;
import dev.hycolony.core.kernel.BlockPos;
import dev.hycolony.core.kernel.item.ItemKey;
import dev.hycolony.core.logistics.pickup.KeepRule;
import java.util.List;
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

    @Test
    void sixtyFourSeedsOfEachOwnedFieldAreKept() {
        FarmerColony c = new FarmerColony(1);
        FarmField a = c.field(3, true);
        c.fields().assign(c.colony, c.hut, a);
        c.field(6, true).setSeed(Optional.of(new ItemKey("Plant_Seeds_Carrot"))); // free: not kept

        List<KeepRule> rules = c.fields().keepRules(c.colony, c.hut);

        assertEquals(1, rules.size());
        assertTrue(rules.getFirst().matches().test(FarmerColony.WHEAT_SEEDS));
        assertEquals(FarmerFieldsModule.SEEDS_KEPT, rules.getFirst().amount());
    }

    @Test
    void passInProgressSurvivesSaveAndLoad() {
        FarmerColony c = new FarmerColony(1);
        FieldWalk walk = c.fields().walk();
        walk.advance(FieldRadii.defaults());
        walk.advance(FieldRadii.defaults());
        walk.setPrevPos(Optional.of(new BlockPos(1, 63, 0)));
        JsonObject saved = new JsonObject();
        c.fields().write(saved);

        FarmerFieldsModule back = new FarmerFieldsModule();
        back.read(saved);

        assertArrayEquals(walk.offset().orElseThrow(), back.walk().offset().orElseThrow());
        assertEquals(walk.prevPos(), back.walk().prevPos());
        walk.advance(FieldRadii.defaults());
        back.walk().advance(FieldRadii.defaults());
        assertArrayEquals(walk.offset().orElseThrow(), back.walk().offset().orElseThrow(), "same next cell");
    }

    @Test
    void unreadableModuleValuesFallBack() {
        JsonObject saved = new JsonObject();
        saved.addProperty("assignManually", "maybe");
        saved.addProperty("cell", "twelve");
        JsonArray checked = new JsonArray();
        JsonObject day = new JsonObject();
        day.addProperty("day", "monday");
        JsonArray pos = new JsonArray();
        pos.add(3);
        pos.add(64);
        pos.add(0);
        day.add("pos", pos);
        checked.add(day);
        saved.add("checked", checked);

        FarmerFieldsModule back = new FarmerFieldsModule();
        back.read(saved);

        assertFalse(back.assignManually());
        assertTrue(back.walk().offset().isEmpty());
    }

    @Test
    void fertilizerIsKeptOnlyWhileRequested() {
        FarmerColony c = new FarmerColony(1);
        FarmerSettingsModule settings = c.hut.module(FarmerSettingsModule.class).orElseThrow();
        ItemKey fertilizer = c.colony.context().ports().farming().fertilizerItem();

        List<KeepRule> on = settings.keepRules(c.colony, c.hut);
        settings.setFertilize(false);

        assertEquals(1, on.size());
        assertTrue(on.getFirst().matches().test(fertilizer));
        assertEquals(1, on.getFirst().amount());
        assertTrue(settings.keepRules(c.colony, c.hut).isEmpty());
    }

    @Test
    void fertilizeSettingSurvivesSaveAndLoadAndDefaultsOn() {
        FarmerSettingsModule settings = new FarmerSettingsModule();
        settings.setFertilize(false);
        JsonObject saved = new JsonObject();
        settings.write(saved);
        FarmerSettingsModule back = new FarmerSettingsModule();
        back.read(saved);
        FarmerSettingsModule fresh = new FarmerSettingsModule();
        JsonObject garbled = new JsonObject();
        garbled.addProperty("fertilize", "yes please");
        fresh.read(garbled);

        assertFalse(back.fertilize());
        assertTrue(fresh.fertilize(), "an unreadable value keeps the default");
    }
}
