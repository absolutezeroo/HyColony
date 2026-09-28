package dev.hycolony.core.farming.job;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.hycolony.core.farming.CropState;
import dev.hycolony.core.farming.field.FarmField;
import dev.hycolony.core.farming.field.FieldStage;
import dev.hycolony.core.request.model.ToolRequest;
import org.junit.jupiter.api.Test;

/** MC EntityAIWorkFarmer.prepareForFarming and canGoPlanting. */
class FarmWorkPrepareTest extends FarmerTestBase {
    @Test
    void levelZeroHutStaysPreparing() {
        hut.setLevel(0);
        field(true);
        assertEquals(FarmerState.PREPARING, work.prepare());
    }

    @Test
    void noFieldBlocksTheFarmer() {
        assertEquals(FarmerState.IDLE, work.prepare());
        assertTrue(work.status().isPresent());
    }

    @Test
    void missingHoeIsRequested() {
        field(true);
        assertEquals(FarmerState.PREPARING, work.prepare());
        assertTrue(colony.requests().byRequester(hut.requesterId()).stream()
                .anyMatch(r -> r.requestable() instanceof ToolRequest)); // the hoe (MC checkForToolOrWeapon)
    }

    @Test
    void fertilizerIsRequestedOnceWhenNoneAnywhere() {
        field(true);
        give(HOE, 1);
        work.prepare();
        work.prepare();
        assertEquals(1, requestsFor(FERTILIZER).size());
    }

    @Test
    void noFertilizerRequestWhenTheSettingIsOff() {
        field(true);
        give(HOE, 1);
        settings().setFertilize(false);
        work.prepare();
        assertTrue(requestsFor(FERTILIZER).isEmpty());
    }

    @Test
    void fertilizerInTheHutIsFetched() {
        field(true);
        give(HOE, 1);
        putInHut(FERTILIZER, 1);
        work.prepare();
        assertEquals(1, carried(FERTILIZER));
    }

    @Test
    void emptyStageWithWorkGoesHoeing() {
        field(true);
        give(HOE, 1);
        give(FERTILIZER, 1);
        assertEquals(FarmerState.FARMER_HOE, work.prepare());
    }

    @Test
    void hoedStageWithSeedsGoesPlanting() {
        FarmField f = field(true);
        give(HOE, 1);
        give(FERTILIZER, 1);
        give(SEEDS, 8);
        f.nextStage();
        assertEquals(FarmerState.FARMER_PLANT, work.prepare());
    }

    @Test
    void plantedStageWithAMatureCropGoesHarvesting() {
        FarmField f = field(true);
        give(HOE, 1);
        give(FERTILIZER, 1);
        f.nextStage();
        f.nextStage();
        var cell = cells().get(3);
        t.farming.tillable.remove(cell);
        t.farming.tilled.add(cell);
        t.farming.plant(cell.offset(0, 1, 0), SEEDS);
        t.farming.cropState.put(cell.offset(0, 1, 0), CropState.MATURE);
        assertEquals(FarmerState.FARMER_HARVEST, work.prepare());
    }

    @Test
    void noSeedAnywhereAsksOnceAndSkipsPlanting() {
        FarmField f = field(true);
        give(HOE, 1);
        give(FERTILIZER, 1);
        f.nextStage();

        assertEquals(FarmerState.PREPARING, work.prepare());
        assertEquals(FieldStage.PLANTED, f.stage());
        f.nextStage();
        f.nextStage();
        f.nextStage(); // back to HOED
        work.prepare();

        assertEquals(1, requestsFor(SEEDS).size());
    }

    @Test
    void seedsInTheHutAreTakenBeforeAsking() {
        FarmField f = field(true);
        give(HOE, 1);
        give(FERTILIZER, 1);
        putInHut(SEEDS, 10);
        f.nextStage();

        work.prepare();

        assertEquals(10, carried(SEEDS));
        assertTrue(requestsFor(SEEDS).isEmpty());
        assertEquals(FieldStage.HOED, f.stage());
    }

    @Test
    void nothingToDoSkipsTheStageAndFourSkipsReleaseTheField() {
        FarmField f = field(true);
        give(HOE, 1);
        give(FERTILIZER, 1);
        t.farming.tillable.clear(); // nothing to hoe; PLANTED has nothing to harvest; HOED has no seed: a request
        f.nextStage();
        f.nextStage();

        for (int i = 0; i < 10 && fields().currentField(colony, hut).isPresent() || i == 0; i++) {
            work.prepare();
        }

        assertTrue(fields().currentField(colony, hut).isEmpty());
        assertTrue(fields().fieldToWorkOn(colony, hut).isEmpty(), "done for today");
    }
}
