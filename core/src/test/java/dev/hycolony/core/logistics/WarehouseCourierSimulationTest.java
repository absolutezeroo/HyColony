package dev.hycolony.core.logistics;

import static dev.hycolony.core.testing.FakeBlueprints.CHEST_I;
import static dev.hycolony.core.testing.FakeBlueprints.DIRT_I;
import static dev.hycolony.core.testing.FakeBlueprints.HUT_BLOCK;
import static dev.hycolony.core.testing.FakeBlueprints.PLANKS_I;
import static dev.hycolony.core.testing.FakeBlueprints.TORCH_I;
import static dev.hycolony.core.testing.FakeBlueprints.state;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.hycolony.core.building.Building;
import dev.hycolony.core.construction.blueprint.StructurePlan;
import dev.hycolony.core.construction.workorder.WorkOrderType;
import dev.hycolony.core.kernel.BlockPos;
import dev.hycolony.core.kernel.item.ItemKey;
import dev.hycolony.core.logistics.pickup.PickupRequests;
import dev.hycolony.core.request.Request;
import dev.hycolony.core.request.model.Delivery;
import dev.hycolony.core.request.model.RequestState;
import dev.hycolony.core.request.model.StackRequest;
import dev.hycolony.core.request.resolver.PlayerResolver;
import dev.hycolony.core.request.resolver.RetryingResolver;
import dev.hycolony.core.testing.FakeBlueprints;
import java.nio.file.Files;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;

/**
 * The warehouse, the courier and the builder working together (SP3a task 10): the courier brings the warehouse's
 * stock to the builder, empties the builder hut back into the warehouse, stops at a full warehouse, and an old save
 * without any warehouse picks the new system up.
 */
class WarehouseCourierSimulationTest extends LogisticsSimulation {
    private static final Map<ItemKey, Integer> STOCK = Map.of(PLANKS_I, 64, TORCH_I, 4, CHEST_I, 2);

    @Test
    void courierBringsTheWarehouseStockAndTakesTheSurplusBack() {
        Building warehouse = openWarehouse(STOCK);
        Building hut = builderHut();
        Map<ItemKey, Integer> before = totals(PLANKS_I, TORCH_I, CHEST_I);
        order(BUILDER_HUT, WorkOrderType.BUILD);

        runUntil(() -> hut.level() == 1, MAX_TICKS);

        assertEquals(64 - 24, stored(RACK, PLANKS_I), "the plan's 24 planks came from the rack");
        assertEquals(4 - 1, stored(RACK, TORCH_I));
        assertEquals(2 - 1, stored(RACK, CHEST_I));
        List<Request> stacks = seen.values().stream()
                .filter(r -> r.requestable() instanceof StackRequest)
                .toList();
        assertFalse(stacks.isEmpty());
        String stock = "warehouse:" + warehouse.requesterId().value();
        for (Request r : stacks) {
            Set<String> by = resolversSeen.getOrDefault(r.token(), Set.of());
            assertTrue(by.contains(stock), () -> r + " served by " + by);
            assertFalse(by.contains(PlayerResolver.ID) || by.contains(RetryingResolver.ID), () -> r + " by " + by);
        }
        assertTrue(
                seen.values().stream()
                        .anyMatch(r -> r.requestable() instanceof Delivery && r.state() == RequestState.RECEIVED),
                "the courier delivered");
        assertEquals(before, totals(PLANKS_I, TORCH_I, CHEST_I));

        // What the builder left in its hut: a forced pickup brings it back to the warehouse.
        t.containers.containers.get(BUILDER_HUT).put(DIRT_I, 10);
        assertTrue(PickupRequests.createPickupRequest(colony, hut, 10, true));
        runUntil(() -> stored(RACK, DIRT_I) + stored(WAREHOUSE, DIRT_I) == 10, 20_000);
        assertEquals(0, t.containers.count(hut.containers(), DIRT_I));
        runUntil(() -> colony.requests().all().isEmpty(), 2_000);
        assertEquals(10, total(DIRT_I));
    }

    /** Review point 4: a full warehouse sends its message and the courier keeps the items until there is room. */
    @Test
    void fullWarehouseKeepsThePickedUpItemsWithTheCourierUntilThereIsRoom() {
        Building warehouse = openWarehouse(Map.of(PLANKS_I, 64));
        t.containers.slots.put(WAREHOUSE, 0);
        t.containers.slots.put(RACK, 1); // the planks fill it
        Building hut = builderHut();
        t.containers.containers.put(BUILDER_HUT, new LinkedHashMap<>(Map.of(DIRT_I, 10)));
        assertTrue(PickupRequests.createPickupRequest(colony, hut, 10, true));

        runUntil(() -> courier().inventory().count(DIRT_I) == 10 && fullMessages() > 0, 20_000);
        run(2_000);
        assertEquals(10, courier().inventory().count(DIRT_I), "kept, not lost");
        assertEquals(1, fullMessages(), "at most one message every five minutes");
        assertEquals(10, total(DIRT_I));

        t.containers.slots.put(RACK, 2); // the player adds room
        runUntil(() -> stored(RACK, DIRT_I) == 10, 2_000);
        assertEquals(0, courier().inventory().count(DIRT_I));
        assertEquals(64, stored(RACK, PLANKS_I));
        assertTrue(couriers(warehouse).contains(courier().id()));
    }

    /** Review point 5: a v2 save, from before the warehouse, loads; a warehouse placed afterwards serves its builder. */
    @Test
    void midbuildV2SaveIsFinishedFromANewWarehouse() throws Exception {
        try (var in = getClass().getResourceAsStream("/fixtures/colony-v2-midbuild.json")) {
            Files.write(dir.resolve("colony-1.json"), in.readAllBytes());
        }
        BlockPos fixtureHut = new BlockPos(70, 64, 0);
        t.blocks.blocks.put(fixtureHut, state(HUT_BLOCK));
        // The world as the fixture left it: the site cleared, the first 12 SOLID blocks of the hut placed.
        StructurePlan plan = StructurePlan.build(FakeBlueprints.hut(false), fixtureHut, t.catalog);
        for (int i = 0; i < 12; i++) {
            t.blocks.blocks.put(
                    plan.solidPositions().get(i), plan.solidList().get(i).state());
        }
        manager = newManager();
        manager.persistence().loadAll();
        colony = manager.byId(1).orElseThrow();
        watch();
        Building hut = colony.buildings().at(fixtureHut).orElseThrow();
        assertTrue(
                colony.buildings().all().stream().noneMatch(b -> b.type().id().contains("warehouse")));

        openWarehouse(Map.of(PLANKS_I, 64));
        runUntil(() -> hut.level() == 1, MAX_TICKS);

        assertEquals(64 - 12, stored(RACK, PLANKS_I), "the 12 planks the save still asked for");
        assertEquals(0, t.playerInventory.count(alice, PLANKS_I));
    }

    private long fullMessages() {
        return t.notifier.sent.stream()
                .filter(s -> s.msg().key().startsWith("hycolony.warehouse.full"))
                .count();
    }

    private Map<ItemKey, Integer> totals(ItemKey... items) {
        Map<ItemKey, Integer> out = new LinkedHashMap<>();
        for (ItemKey i : items) {
            out.put(i, total(i));
        }
        return out;
    }
}
