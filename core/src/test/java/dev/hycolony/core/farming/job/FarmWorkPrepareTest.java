package dev.hycolony.core.farming.job;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.hycolony.core.colony.Colony;
import dev.hycolony.core.farming.CropState;
import dev.hycolony.core.farming.field.FarmField;
import dev.hycolony.core.farming.field.FieldRadii;
import dev.hycolony.core.farming.field.FieldStage;
import dev.hycolony.core.job.JobStatus;
import dev.hycolony.core.job.work.SyncRequests;
import dev.hycolony.core.job.work.WorkerStock;
import dev.hycolony.core.kernel.BlockPos;
import dev.hycolony.core.kernel.item.ItemAmount;
import dev.hycolony.core.request.Request;
import dev.hycolony.core.request.model.RequestState;
import dev.hycolony.core.request.model.RequestToken;
import dev.hycolony.core.request.model.ToolRequest;
import java.util.List;
import java.util.Optional;
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
        assertEquals(JobStatus.STUCK, citizen.jobStatus()); // MC prepareForFarming
    }

    @Test
    void aFarmerWithAFieldAndAHoeIsWorking() {
        citizen.setJobStatus(JobStatus.STUCK);
        field(true);
        give(HOE, 1);
        work.prepare();
        assertEquals(JobStatus.WORKING, citizen.jobStatus()); // MC holdEfficientTool
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
        assertTrue(requestsFor(SEEDS).get(0).async(), "MC checkIfRequestForItemExistOrCreateAsync");
    }

    /**
     * MC checkIfRequestForItemExistOrCreate: no new request while one is open or completed; once cleanAsync received
     * the delivered one, the item is asked for again.
     */
    @Test
    void seedsAreAskedAgainOnceTheDeliveredOnesAreReceived() {
        FarmField f = field(true);
        give(HOE, 1);
        give(FERTILIZER, 1);
        f.nextStage();
        work.prepare(); // none anywhere: asks, skips planting
        Request first = requestsFor(SEEDS).get(0);
        colony.requests().overrule(first.token(), List.of(new ItemAmount(SEEDS, 64)), false); // a player supplies them
        putInHut(SEEDS, 64);
        f.nextStage();
        f.nextStage(); // PLANTED, EMPTY, then HOED again
        work.prepare(); // takes the delivered seeds
        assertEquals(64, carried(SEEDS));
        citizen.inventory().extract(SEEDS, 64); // all planted
        f.nextStage();
        f.nextStage();
        f.nextStage();

        work.prepare();
        assertEquals(List.of(first.token()), tokens(requestsFor(SEEDS)), "the completed one still counts");
        sync().cleanAsync();
        f.nextStage();
        f.nextStage(); // skipped to PLANTED without seeds, so EMPTY, then HOED again
        work.prepare();

        assertEquals(1, open(requestsFor(SEEDS)));
    }

    @Test
    void fertilizerIsAskedAgainOnceTheDeliveredOneIsReceived() {
        field(true);
        give(HOE, 1);
        work.prepare();
        Request first = requestsFor(FERTILIZER).get(0);
        colony.requests().overrule(first.token(), List.of(new ItemAmount(FERTILIZER, 1)), false);

        work.prepare();
        assertEquals(List.of(first.token()), tokens(requestsFor(FERTILIZER)), "the completed one still counts");
        sync().cleanAsync();
        work.prepare();

        assertEquals(1, open(requestsFor(FERTILIZER)));
    }

    private SyncRequests sync() {
        return new SyncRequests(colony, citizen, hut, new WorkerStock(colony, citizen, hut, 64));
    }

    private static List<RequestToken> tokens(List<Request> requests) {
        return requests.stream().map(Request::token).toList();
    }

    private static long open(List<Request> requests) {
        return requests.stream()
                .filter(r -> r.state().isBefore(RequestState.COMPLETED))
                .count();
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

    @Test
    void fourSkipsMakeTheNextPassLeaveItsField() {
        field(true);
        colony.setDay(1);
        for (int i = 0; i < FarmWork.MAX_SKIPS; i++) {
            work.skipped();
        }
        colony.setDay(2);
        assertTrue(fields().fieldToWorkOn(colony, hut).isPresent());

        work.endPass(false);

        assertTrue(fields().fieldToWorkOn(colony, hut).isEmpty(), "MC prepareForFarming sets didWork on the 4th skip");
    }

    @Test
    void preparingAgainResumesAfterTheCellInProgress() {
        FarmField f = field(true);
        give(HOE, 1);
        settings().setFertilize(false);
        f.nextStage();
        f.nextStage();
        for (int i : new int[] {1, 5}) {
            var cell = cells().get(i);
            t.farming.tillable.remove(cell);
            t.farming.tilled.add(cell);
            t.farming.plant(cell.offset(0, 1, 0), SEEDS);
            t.farming.cropState.put(cell.offset(0, 1, 0), CropState.MATURE);
        }
        fields().fieldToWorkOn(colony, hut);
        for (int i = 0; i < 3; i++) {
            fields().walk().advance(f.radii()); // mid-pass, past cell 1 (MC: a dump cut the pass)
        }

        assertEquals(FarmerState.FARMER_HARVEST, work.prepare());

        var o = fields().walk().offset().orElseThrow();
        assertEquals(cells().get(5), FIELD.offset(o[0], -1, o[1]), "MC nextValidCell goes on from workingOffset");
    }

    @Test
    void fieldBrokenWhileAwayMakesTheNextFieldStartFromItsFirstCell() {
        field(true);
        give(HOE, 1);
        settings().setFertilize(false);
        work.prepare();
        work.workAtField(FarmerState.FARMER_HOE);
        work.workAtField(FarmerState.FARMER_HOE); // mid-pass on the first field, then away (a dump)
        BlockPos other = FIELD.offset(10, 0, 0);
        colony.registries().fields().add(other);
        FarmField b = colony.registries().fields().get(other).orElseThrow();
        b.setRadii(new FieldRadii(1, 1, 1, 1));
        b.setSeed(Optional.of(SEEDS));
        b.setOwner(Optional.of(HUT));
        b.nextStage();
        give(SEEDS, 8);

        colony.registries().fields().remove(FIELD);

        assertEquals(FarmerState.FARMER_PLANT, work.prepare());
        assertTrue(fields().walk().offset().isEmpty(), "the planting pass starts from the first cell");
    }

    @Test
    void fieldCleanedAwayMarksTheColonyToSave() {
        field(true);
        t.farming.fieldBlocks.clear(); // the field block is gone
        t.players.online.put(OWNER, HUT); // keeps the colony active
        colony.clearDirty();

        for (int i = 0; i <= 2 * Colony.SLOW_TICK; i++) { // the colony turns ACTIVE first
            t.clock.tick++;
            colony.tick();
        }

        assertTrue(colony.registries().fields().get(FIELD).isEmpty());
        assertTrue(colony.isDirty());
    }

    @Test
    void fieldsDoneForTodaySayWhyTheFarmerWaits() {
        field(true);
        give(HOE, 1);
        settings().setFertilize(false);
        colony.setDay(1);
        fields().fieldToWorkOn(colony, hut);
        fields().resetCurrentField(colony);

        assertEquals(FarmerState.IDLE, work.prepare());

        assertEquals(
                "hycolony.farmer.fieldsDoneToday", work.status().orElseThrow().key());
    }
}
