package dev.hycolony.core.logistics.courier;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.hycolony.core.citizen.Skill;
import dev.hycolony.core.kernel.item.ItemAmount;
import dev.hycolony.core.request.model.RequestToken;
import dev.hycolony.core.request.model.StackRequest;
import java.util.List;
import org.junit.jupiter.api.Test;

/** MC EntityAIWorkDeliveryman.prepareDelivery and deliver. */
class CourierDeliveryTest extends CourierAITestBase {
    void deliversFromTheRackToTheTarget() {
        hire();
        put(RACK, STONE, 8);
        RequestToken task = delivery(RACK, STONE, 5);

        runUntil(() -> completed(task));

        assertEquals(3, stored(RACK, STONE));
        assertEquals(5, stored(target.position(), STONE));
        assertEquals(0, carried(STONE));
        assertTrue(job.taskQueue().isEmpty());
        assertTrue(job.ongoingDeliveries().isEmpty());
        assertEquals(awarded(1.5), citizen.skills().experience(Skill.Agility), 1e-9);
    }

    @Test
    void parallelDeliveriesToOnePlaceAreLoadedAndResolvedTogether() {
        hire(5, 5); // 2 at once
        put(RACK, STONE, 5);
        put(OTHER_RACK, DIRT, 3);
        RequestToken stone = delivery(RACK, STONE, 5);
        RequestToken dirt = delivery(OTHER_RACK, DIRT, 3);

        runUntil(() -> completed(stone));

        assertTrue(completed(dirt));
        assertEquals(5, stored(target.position(), STONE));
        assertEquals(3, stored(target.position(), DIRT));
        assertEquals(awarded(1.5), citizen.skills().experience(Skill.Agility), 1e-9); // one trip, one award
    }

    @Test
    void itNeverLoadsMoreThanItsParallelCount() {
        hire(5, 5); // 2 at once when picked
        put(RACK, STONE, 5);
        put(OTHER_RACK, DIRT, 3);
        RequestToken stone = delivery(RACK, STONE, 5);
        RequestToken dirt = delivery(OTHER_RACK, DIRT, 3);
        runUntil(() -> job.taskQueue().size() == 2);

        citizen.skills().set(Skill.Adaptability, 1, 0); // now 1 at once

        runUntil(() -> completed(stone));
        assertFalse(completed(dirt));
        assertEquals(3, stored(OTHER_RACK, DIRT));
        runUntil(() -> completed(dirt)); // on the next trip
    }

    @Test
    void anEmptiedSourceFailsTheDelivery() {
        hire();
        put(RACK, STONE, 2); // 5 asked
        RequestToken task = delivery(RACK, STONE, 5);

        runUntil(() -> failed(task));

        assertEquals(0, carried(STONE));
        assertEquals(2, stored(RACK, STONE));
        assertTrue(job.taskQueue().isEmpty());
        assertTrue(job.ongoingDeliveries().isEmpty());
    }

    @Test
    void aMissingCompanionStackLetsTheLoadedOnesGo() {
        hire(5, 5); // 2 at once
        put(RACK, STONE, 5);
        RequestToken stone = delivery(RACK, STONE, 5);
        RequestToken dirt = delivery(OTHER_RACK, DIRT, 3); // the rack has none

        runUntil(() -> completed(stone));

        assertFalse(failed(dirt));
        assertEquals(List.of(dirt), job.taskQueue());
    }

    @Test
    void aVanishedTargetResolvesTheDelivery() {
        hire();
        put(RACK, STONE, 5);
        RequestToken task = delivery(RACK, STONE, 5);
        runUntil(() -> carried(STONE) == 5);

        colony.buildings().remove(target.position());

        runUntil(() -> job.taskQueue().isEmpty());
        assertFalse(failed(task));
    }

    @Test
    void aFullTargetSwapsOutAStackItDoesNotNeed() {
        hire();
        t.containers.slots.put(target.position(), 1);
        put(target.position(), DIRT, 7); // not in any open request of the target
        put(RACK, STONE, 5);
        RequestToken task = delivery(RACK, STONE, 5);

        runUntil(() -> completed(task));

        assertEquals(5, stored(target.position(), STONE));
        assertEquals(0, stored(target.position(), DIRT));
        runUntil(() -> stored(warehouse.position(), DIRT) == 7); // carried back and stored
    }

    @Test
    void aFullTargetKeepingItsStacksLeavesTheItemsWithTheCourier() {
        hire();
        t.containers.slots.put(target.position(), 1);
        put(target.position(), DIRT, 7);
        // an open request of the target's citizen already received this dirt (MC isItemStackInRequest)
        RequestToken open = m.createAndAssign(target, new StackRequest(DIRT, 64, 64, false), 7);
        m.addDelivery(open, new ItemAmount(DIRT, 7));
        put(RACK, STONE, 5);
        RequestToken task = delivery(RACK, STONE, 5);

        runUntil(() -> completed(task)); // MC finishRequest(true) even when nothing fitted

        assertEquals(7, stored(target.position(), DIRT));
        runUntil(() -> stored(warehouse.position(), STONE) == 5); // then dumped at the warehouse
    }

    @Test
    void aFullInventoryWhilePreparingGoesDumping() {
        t.catalog.maxStacks.put(STONE, 1);
        hire(5, 5); // 2 at once
        put(RACK, STONE, 27);
        put(OTHER_RACK, DIRT, 1);
        RequestToken stone = delivery(RACK, STONE, 27); // fills the 27 slots
        delivery(OTHER_RACK, DIRT, 1);

        runUntil(() -> carried(STONE) == 27);
        runUntil(() -> "DUMPING".equals(ai.stateName()));

        assertFalse(completed(stone));
    }
}
