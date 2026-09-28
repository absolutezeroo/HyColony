package dev.hycolony.core.farming.job;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.hycolony.core.job.JobAI;
import dev.hycolony.core.kernel.item.ItemKey;
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
    void farmerWithoutFieldTellsWhy() {
        JobAI ai = job.createAI(colony, body);

        runUntil(ai, () -> ai.describe().isPresent());

        assertEquals("hycolony.farmer.noFields", ai.describe().orElseThrow().key());
    }

    private void runUntil(JobAI ai, BooleanSupplier done) {
        for (int i = 0; i < 20_000 && !done.getAsBoolean(); i++) {
            t.clock.tick++;
            ai.tick();
        }
        assertTrue(done.getAsBoolean(), () -> "never happened; farmer in " + ai.stateName());
    }
}
