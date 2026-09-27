package dev.hycolony.core.logistics.courier;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.hycolony.core.citizen.Skill;
import dev.hycolony.core.kernel.Vec3;
import dev.hycolony.core.kernel.item.ItemAmount;
import dev.hycolony.core.request.model.RequestToken;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

/** MC EntityAIWorkDeliveryman: decide, dump, working state, rain, speed and finishRequest. */
class DeliverymanAITest extends CourierAITestBase {
    @Test
    void withoutTaskItWaitsAtTheWarehouse() {
        hire();

        run(300);

        assertEquals("START_WORKING", ai.stateName());
        assertTrue(Vec3.center(warehouse.position()).distance(t.bodies.bodies.get(body).position) < 3);
    }

    @Test
    void withoutTaskItStoresWhatItCarriesAtTheWarehouse() {
        hire();
        citizen.inventory().insert(new ItemAmount(LOG, 4), i -> 64);

        runUntil(() -> carried(LOG) == 0);

        assertEquals(4, stored(warehouse.position(), LOG));
    }

    @Test
    void aDeliveryStartsWithAnEmptyInventory() {
        hire();
        citizen.inventory().insert(new ItemAmount(LOG, 4), i -> 64);
        put(RACK, STONE, 5);
        RequestToken task = delivery(RACK, STONE, 5);

        runUntil(() -> completed(task));

        assertEquals(4, stored(warehouse.position(), LOG));
        assertEquals(5, stored(target.position(), STONE));
    }

    @Test
    void withoutWarehouseItDoesNotWork() {
        hire();
        run(DeliverymanAI.DECISION_DELAY + 20);
        assertTrue(job.isWorking());

        colony.buildings().remove(warehouse.position());
        colony.buildings().onColonyTick(colony);
        run(200);

        assertFalse(job.isWorking());
    }

    @Test
    void speedGrowsWithAgility() {
        hire(50, 0);

        assertEquals(1.5, t.bodies.bodies.get(body).speed, 1e-9); // (0.3 + 50 x 0.003) / 0.3

        citizen.skills().set(Skill.Agility, 10, 0);
        run(200);

        assertEquals(1.1, t.bodies.bodies.get(body).speed, 1e-9);
    }

    @Test
    void finishingAFailedHeadWithNothingLoadedStillPopsIt() {
        hire();
        RequestToken task = delivery(RACK, STONE, 5);
        assertEquals(Optional.of(task), job.currentTask(colony).map(r -> r.token()));

        job.finishRequest(colony, false);

        assertTrue(failed(task));
        assertEquals(List.of(), job.taskQueue());
    }
}
