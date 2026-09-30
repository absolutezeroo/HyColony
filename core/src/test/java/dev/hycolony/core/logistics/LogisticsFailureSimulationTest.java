package dev.hycolony.core.logistics;

import static dev.hycolony.core.testing.FakeBlueprints.CHEST_I;
import static dev.hycolony.core.testing.FakeBlueprints.PLANKS_I;
import static dev.hycolony.core.testing.FakeBlueprints.TORCH_I;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.hycolony.core.building.Building;
import dev.hycolony.core.citizen.CitizenData;
import dev.hycolony.core.colony.Colony;
import dev.hycolony.core.construction.workorder.WorkOrderType;
import dev.hycolony.core.job.HiringMode;
import dev.hycolony.core.kernel.item.ItemAmount;
import dev.hycolony.core.kernel.item.ItemKey;
import dev.hycolony.core.request.Request;
import dev.hycolony.core.request.model.Delivery;
import dev.hycolony.core.request.model.RequestState;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/**
 * The plan's review points 1 to 4, played end to end: a rack emptied, the courier fired and the warehouse broken, each
 * in the middle of a delivery. Nothing blocks for good, no request is left behind, no item is duplicated or lost.
 */
class LogisticsFailureSimulationTest extends LogisticsSimulation {
    private static final Map<ItemKey, Integer> STOCK = Map.of(PLANKS_I, 64, TORCH_I, 4, CHEST_I, 2);

    /** Review point 1: the player empties the rack before the courier loads; the builder is served another way. */
    @Test
    void rackEmptiedBeforeLoadingFailsTheDeliveryAndThePlayerServesTheBuilder() {
        openWarehouse(STOCK);
        Building hut = builderHut();
        order(BUILDER_HUT, WorkOrderType.BUILD);
        runUntil(
                () -> !courierJob().taskQueue().isEmpty()
                        && courier().inventory().contents().isEmpty(),
                MAX_TICKS);
        int planks = total(PLANKS_I);

        int taken = t.containers.extract(List.of(RACK), PLANKS_I, 64); // the player takes the planks
        t.playerInventory.give(alice, new ItemAmount(PLANKS_I, taken));
        autoFulfil = true;
        runUntil(() -> hut.level() == 1, MAX_TICKS);

        assertTrue(failedDeliveries() > 0, "the delivery from the emptied rack failed");
        assertEquals(planks, total(PLANKS_I));
        runUntil(() -> openDeliveries() == 0, 2_000);
    }

    /** Review point 2: the courier fired while carrying; its tasks go back, its load is kept, then stored. */
    @Test
    void courierFiredMidDeliveryKeepsItsLoadAndTheWorkResumesWhenRehired() {
        Building warehouse = openWarehouse(STOCK);
        Building hut = builderHut();
        order(BUILDER_HUT, WorkOrderType.BUILD);
        runUntil(() -> courier().inventory().count(PLANKS_I) > 0, MAX_TICKS);
        CitizenData courier = courier();
        int load = courier.inventory().count(PLANKS_I);
        int planks = total(PLANKS_I);

        assertTrue(manager.huts().setHiring(alice, COURIER_HUT, HiringMode.MANUAL));
        assertTrue(manager.huts().fire(alice, COURIER_HUT, courier.id()));
        run(Colony.SLOW_TICK); // the warehouse drops a courier on its colony tick

        assertTrue(courier.job().isEmpty());
        assertEquals(load, courier.inventory().count(PLANKS_I), "the load stays with the citizen");
        assertTrue(couriers(warehouse).isEmpty());
        assertEquals(planks, total(PLANKS_I));
        assertTrue(failedDeliveries() > 0, "its tasks failed back to the request system");

        assertTrue(manager.huts().hire(alice, COURIER_HUT, courier.id()));
        runUntil(() -> hut.level() == 1, MAX_TICKS);
        runUntil(() -> courier.inventory().count(PLANKS_I) == 0, 2_000);
        assertEquals(planks, total(PLANKS_I));
        assertEquals(64 - 24, stored(RACK, PLANKS_I), "the load went back to the rack, the plan took 24");
        assertEquals(0, t.playerInventory.count(alice, PLANKS_I));
    }

    /**
     * Review point 3: the warehouse broken while its courier carries; its queued requests are cancelled or go
     * elsewhere, none is left orphaned, and the player finishes the build.
     */
    @Test
    void warehouseBrokenMidDeliveryLeavesNoOrphanAndLosesNothing() {
        Building warehouse = openWarehouse(STOCK);
        Building hut = builderHut();
        order(BUILDER_HUT, WorkOrderType.BUILD);
        runUntil(() -> courier().inventory().count(PLANKS_I) > 0, MAX_TICKS);
        int load = courier().inventory().count(PLANKS_I);
        int planks = total(PLANKS_I);

        t.blocks.blocks.remove(WAREHOUSE);
        manager.huts().onRemoved(WAREHOUSE, UUID.randomUUID());
        tick();

        assertTrue(colony.buildings().at(WAREHOUSE).isEmpty());
        String warehouseId = warehouse.requesterId().value();
        for (Request r : colony.requests().all()) {
            assertFalse(r.requestable() instanceof Delivery, () -> "delivery left: " + r);
            String by = colony.requests()
                    .resolverOf(r.token())
                    .map(res -> res.resolverId())
                    .orElse("");
            assertFalse(by.endsWith(warehouseId), () -> r + " still with " + by);
        }
        assertFalse(colony.requests().cancelOrphans(), "no orphan left");
        assertTrue(courierJob().taskQueue().isEmpty());

        t.playerInventory.give(alice, new ItemAmount(PLANKS_I, 64));
        t.playerInventory.give(alice, new ItemAmount(TORCH_I, 1));
        t.playerInventory.give(alice, new ItemAmount(CHEST_I, 1));
        autoFulfil = true;
        runUntil(() -> hut.level() == 1, MAX_TICKS);

        assertEquals(load, courier().inventory().count(PLANKS_I), "no warehouse to store into: kept, not lost");
        assertEquals(planks + 64, total(PLANKS_I));
        runUntil(() -> colony.requests().all().isEmpty(), 2_000);
    }

    private long failedDeliveries() {
        return seen.values().stream()
                .filter(r -> r.requestable() instanceof Delivery
                        && (r.state() == RequestState.FAILED || r.state() == RequestState.CANCELLED))
                .count();
    }

    private long openDeliveries() {
        return colony.requests().all().stream()
                .filter(r -> r.requestable() instanceof Delivery)
                .count();
    }
}
