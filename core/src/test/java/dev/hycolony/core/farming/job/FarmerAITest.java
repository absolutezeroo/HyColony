package dev.hycolony.core.farming.job;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.hycolony.core.farming.CropState;
import dev.hycolony.core.job.JobAI;
import dev.hycolony.core.kernel.BlockPos;
import dev.hycolony.core.kernel.Vec3;
import dev.hycolony.core.kernel.item.ItemAmount;
import dev.hycolony.core.kernel.item.ItemKey;
import dev.hycolony.core.kernel.nav.BodyWalker;
import dev.hycolony.core.request.Request;
import dev.hycolony.core.request.model.RequestToken;
import dev.hycolony.core.request.model.StackRequest;
import java.util.List;
import java.util.function.BooleanSupplier;
import org.junit.jupiter.api.Test;

/** MC EntityAIWorkFarmer's state machine: decide, prepare, the field states and the dump after a pass. */
class FarmerAITest extends FarmerTestBase {
    private static final ItemKey STONE = new ItemKey("Rock_Stone");

    @Test
    void idleFarmerWalksToItsFieldAndHoesIt() {
        field(true);
        give(HOE, 1);
        settings().setFertilize(false);
        JobAI ai = job.createAI(colony, body);

        runUntil(ai, () -> t.farming.tilled.containsAll(cells()));
    }

    @Test
    void dumpAfterEveryPass() {
        field(true);
        give(HOE, 1);
        give(STONE, 5);
        settings().setFertilize(false);
        JobAI ai = job.createAI(colony, body);

        runUntil(ai, () -> t.containers.count(hut.containers(), STONE) == 5);

        assertEquals(0, carried(STONE));
        assertEquals(1, carried(HOE), "the hoe stays with the farmer");
    }

    @Test
    void farmerWalksBackToItsHutForEveryDumpDueAtItsField() {
        field(true);
        give(HOE, 1);
        putInHut(SEEDS, 8); // the second pass plants
        settings().setFertilize(false);
        JobAI ai = job.createAI(colony, body);
        // Hoe, plant, harvest: the dumps after the first start a walk home, which the next one must not reuse.
        for (int dumps = 1; dumps <= 3; dumps++) {
            runUntil(ai, () -> distanceTo(FIELD) <= 2);
            give(STONE, 1);
            job.incrementActions(FarmWorkContext.ACTIONS_UNTIL_DUMP); // MC MAX_BLOCKS_MINED, reached at the field
            int stored = dumps;

            runUntil(ai, () -> t.containers.count(hut.containers(), STONE) == stored);

            assertTrue(distanceTo(HUT) <= BodyWalker.ARRIVAL_RANGE, "dump " + dumps + " " + distanceTo(HUT) + " away");
            colony.setDay(colony.day() + 1); // the field had its pass today
            cells().forEach(c -> t.farming.cropState.put(c.offset(0, 1, 0), CropState.MATURE));
        }
    }

    private double distanceTo(BlockPos pos) {
        return t.bodies.position(body).orElseThrow().distance(Vec3.center(pos));
    }

    @Test
    void farmerWithoutFieldTellsWhy() {
        JobAI ai = job.createAI(colony, body);

        runUntil(ai, () -> ai.describe().isPresent());

        assertEquals("hycolony.farmer.noFields", ai.describe().orElseThrow().key());
    }

    /** MC AbstractEntityAIBasic's cleanAsync event, every 200 ticks: a completed hut request is received. */
    @Test
    void aCompletedHutRequestIsReceived() {
        RequestToken token =
                colony.requests().createAndAssign(hut, new StackRequest(SEEDS, 64, 1, true), Request.NO_CITIZEN);
        colony.requests().overrule(token, List.of(new ItemAmount(SEEDS, 64)));
        JobAI ai = job.createAI(colony, body);

        runUntil(ai, () -> colony.requests().get(token).isEmpty());
    }

    private void runUntil(JobAI ai, BooleanSupplier done) {
        for (int i = 0; i < 20_000 && !done.getAsBoolean(); i++) {
            t.clock.tick++;
            ai.tick();
        }
        assertTrue(done.getAsBoolean(), () -> "never happened; farmer in " + ai.stateName());
    }
}
