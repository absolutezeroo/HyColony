package dev.hycolony.core.logistics.courier;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import dev.hycolony.core.app.persistence.ColonySerializer;
import dev.hycolony.core.building.Building;
import dev.hycolony.core.building.BuildingType;
import dev.hycolony.core.citizen.CitizenData;
import dev.hycolony.core.citizen.Skill;
import dev.hycolony.core.colony.Colony;
import dev.hycolony.core.colony.permission.Permissions;
import dev.hycolony.core.colony.territory.TerritoryIndex;
import dev.hycolony.core.construction.hut.ConstructionBuildingTypes;
import dev.hycolony.core.job.WorkerModule;
import dev.hycolony.core.kernel.BlockPos;
import dev.hycolony.core.kernel.item.Inventory;
import dev.hycolony.core.kernel.item.ItemAmount;
import dev.hycolony.core.kernel.item.ItemKey;
import dev.hycolony.core.logistics.warehouse.WarehouseBuilding;
import dev.hycolony.core.logistics.warehouse.WarehouseRequestQueue;
import dev.hycolony.core.request.Request;
import dev.hycolony.core.request.RequestManager;
import dev.hycolony.core.request.Resolver;
import dev.hycolony.core.request.model.Delivery;
import dev.hycolony.core.request.model.Pickup;
import dev.hycolony.core.request.model.RequestState;
import dev.hycolony.core.request.model.RequestToken;
import dev.hycolony.core.request.model.Requestable;
import dev.hycolony.core.request.resolver.PlayerResolver;
import dev.hycolony.core.testing.TestContexts;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class DeliverymanJobTest {
    private static final ItemKey STONE = new ItemKey("Rock_Stone");
    private static final ItemKey LOG = new ItemKey("Wood_Oak_Trunk");
    private static final ItemKey PLANK = new ItemKey("Wood_Oak_Planks");
    private final TestContexts t = new TestContexts();
    private final Colony colony = new Colony(
            t.context(),
            new TerritoryIndex(),
            new Colony.Founding(1, "T", new BlockPos(0, 64, 0), Permissions.createDefault(UUID.randomUUID(), "A")));
    private final RequestManager m = colony.requests();
    private final Building warehouse = building(WarehouseBuilding.TYPE, new BlockPos(0, 64, 0));
    /** Manhattan 9 from the warehouse: a distance malus of 3. */
    private final Building near = residence(new BlockPos(0, 64, 9));
    /** Manhattan 100 from the warehouse: a distance malus of 10. */
    private final Building far = residence(new BlockPos(100, 64, 0));

    private Building building(BuildingType type, BlockPos pos) {
        Building b = Building.create(type, pos, 0);
        b.setLevel(1);
        b.setBuilt(true);
        colony.buildings().add(b);
        return b;
    }

    private Building residence(BlockPos pos) {
        return building(ConstructionBuildingTypes.RESIDENCE, pos);
    }

    /** A courier hired at its own hut, then attached to the warehouse (MC CourierAssignmentModule.onColonyTick). */
    private DeliverymanJob courier(int adaptability) {
        Building hut = building(DeliverymanHut.TYPE, new BlockPos(-20, 64, 0));
        CitizenData citizen = new CitizenData(1);
        citizen.skills().set(Skill.Adaptability, adaptability, 0);
        colony.citizens().restore(citizen);
        assertTrue(hut.module(WorkerModule.class).orElseThrow().hire(colony, hut, citizen));
        colony.buildings().onColonyTick(colony);
        return (DeliverymanJob) citizen.job().orElseThrow();
    }

    private RequestToken delivery(Building to, int priority, ItemKey item) {
        return m.createAndAssign(
                warehouse, new Delivery(warehouse.position(), to.requesterId(), new ItemAmount(item, 1), priority), -1);
    }

    private RequestToken pickup(Building from, int priority, int day) {
        return m.createAndAssign(from, new Pickup(priority, day, 10), -1);
    }

    private List<RequestToken> warehouseQueue() {
        return warehouse.module(WarehouseRequestQueue.class).orElseThrow().tokens();
    }

    private RequestToken current(DeliverymanJob job) {
        return job.currentTask(colony).map(Request::token).orElseThrow();
    }

    private int priority(RequestToken token) {
        Requestable r = m.get(token).orElseThrow().requestable();
        return r instanceof Delivery d ? d.priority() : ((Pickup) r).priority();
    }

    @Test
    void ownQueueFirst() {
        DeliverymanJob job = courier(0);
        RequestToken first = delivery(far, 13, STONE);
        assertEquals(first, current(job));

        RequestToken better = delivery(near, 13, STONE);

        assertEquals(first, current(job));
        assertEquals(List.of(first), job.taskQueue());
        assertEquals(List.of(better), warehouseQueue());
    }

    @Test
    void picksHighestScoreFifoAndDistance() {
        DeliverymanJob job = courier(0);
        Building nearer = residence(new BlockPos(4, 64, 0));
        RequestToken farAway = delivery(far, 13, STONE); // 13 + 3 - 10 = 6
        RequestToken older = delivery(near, 13, STONE); // 13 + 2 - 3 = 12
        RequestToken younger = delivery(nearer, 13, STONE); // 13 + 1 - 2 = 12: the first one wins a tie

        assertEquals(older, current(job));
        assertEquals(List.of(older), job.taskQueue());
        assertEquals(List.of(farAway, younger), warehouseQueue());
        assertEquals(14, priority(farAway)); // skipped over, so it aged
    }

    @Test
    void pickupNotYetDueLosesHundred() {
        DeliverymanJob job = courier(0);
        RequestToken tomorrow = pickup(near, 10, 1); // 10 + 2 - 3 - 100 = -91, 9 if it were due
        RequestToken delivery = delivery(far, 13, STONE); // 13 + 1 - 10 = 4

        assertEquals(delivery, current(job));
        assertEquals(List.of(tomorrow), warehouseQueue());
    }

    @Test
    void duePickupKeepsItsPriority() {
        DeliverymanJob job = courier(0);
        RequestToken today = pickup(near, 10, 0); // 10 + 2 - 3 = 9
        delivery(far, 13, STONE); // 13 + 1 - 10 = 4

        assertEquals(today, current(job));
        assertEquals(List.of(today), job.taskQueue());
    }

    @Test
    void entriesBeforeTheChosenOneAge() {
        DeliverymanJob job = courier(10);
        RequestToken before = delivery(far, 13, STONE); // 13 + 4 - 10 = 7
        RequestToken capped = delivery(far, 14, LOG); // 14 + 3 - 10 = 7
        RequestToken chosen = delivery(near, 13, STONE); // 13 + 2 - 3 = 12
        RequestToken after = delivery(far, 13, PLANK); // 13 + 1 - 10 = 4

        assertEquals(chosen, current(job));

        assertEquals(14, priority(before));
        assertEquals(Delivery.MAX_AGING_PRIORITY, priority(capped));
        assertEquals(13, priority(after));
    }

    @Test
    void sameTargetJoinsUpToParallel() {
        DeliverymanJob job = courier(5); // 1 + 5 / 5 = 2 in parallel
        RequestToken chosen = delivery(near, 13, STONE); // 13 + 4 - 3 = 14
        RequestToken elsewhere = delivery(far, 13, STONE);
        RequestToken joins = delivery(near, 13, LOG);
        RequestToken tooMany = delivery(near, 13, PLANK);

        assertEquals(chosen, current(job));

        assertEquals(List.of(chosen, joins), job.taskQueue());
        assertEquals(List.of(elsewhere, tooMany), warehouseQueue());
    }

    @Test
    void parallelDeliveriesGrowWithAdaptability() {
        DeliverymanJob job = courier(4);
        assertEquals(1, job.maxParallelDeliveries(colony));

        job.citizen().skills().set(Skill.Adaptability, 5, 0);
        assertEquals(2, job.maxParallelDeliveries(colony));

        job.citizen().skills().set(Skill.Adaptability, 14, 0);
        assertEquals(3, job.maxParallelDeliveries(colony));
    }

    @Test
    void pickupGoesLast() {
        DeliverymanJob job = courier(10);
        RequestToken pickup = pickup(near, 14, 0); // 14 + 2 - 3 = 13
        RequestToken sameTarget = delivery(near, 13, STONE); // 13 + 1 - 3 = 11

        assertEquals(pickup, current(job));

        assertEquals(List.of(sameTarget, pickup), job.taskQueue());
        assertTrue(warehouseQueue().isEmpty());
    }

    @Test
    void goneRequestsAreDroppedFromBothQueues() {
        DeliverymanJob job = courier(0);
        RequestToken gone = RequestToken.random();
        warehouseQueue().add(gone);
        RequestToken live = delivery(near, 13, STONE);

        assertEquals(live, current(job));
        assertTrue(warehouseQueue().isEmpty());

        job.read(withQueueHead(job, RequestToken.random()));
        assertEquals(live, current(job));
        assertEquals(List.of(live), job.taskQueue());
    }

    private static JsonObject withQueueHead(DeliverymanJob job, RequestToken head) {
        JsonObject o = job.write();
        JsonArray queue = new JsonArray();
        queue.add(head.id().toString());
        o.getAsJsonArray("queue").forEach(queue::add);
        o.add("queue", queue);
        return o;
    }

    @Test
    void carryLimitPerHutLevel() {
        Building hut = building(DeliverymanHut.TYPE, new BlockPos(-20, 64, 0));
        int[] limits = {2, 3, 5, 9};
        for (int level = 1; level <= 4; level++) {
            hut.setLevel(level);
            assertFalse(DeliverymanHut.cannotHoldMoreItems(hut, inventoryOf(limits[level - 1] - 1)));
            assertTrue(DeliverymanHut.cannotHoldMoreItems(hut, inventoryOf(limits[level - 1])));
        }
        hut.setLevel(DeliverymanHut.MAX_LEVEL);
        assertFalse(DeliverymanHut.cannotHoldMoreItems(hut, inventoryOf(CitizenData.INVENTORY_SLOTS)));
    }

    private static Inventory inventoryOf(int stacks) {
        Inventory inv = new Inventory(CitizenData.INVENTORY_SLOTS);
        for (int i = 0; i < stacks; i++) {
            inv.set(i, Optional.of(new ItemAmount(STONE, 1)));
        }
        return inv;
    }

    @Test
    void inactivityCancelsTasks() {
        DeliverymanJob job = courier(0);
        job.setWorking(colony, true);
        RequestToken task = delivery(near, 13, STONE);
        assertEquals(task, current(job));

        job.setWorking(colony, false);
        for (int i = 1; i < DeliverymanJob.INACTIVITY_LIMIT; i++) {
            colony.citizens().tickData();
        }
        assertEquals(List.of(task), job.taskQueue());
        assertEquals(RequestState.IN_PROGRESS, m.get(task).orElseThrow().state());

        colony.citizens().tickData(); // the 600th update, 36,000 ticks

        assertTrue(job.taskQueue().isEmpty());
        assertTrue(m.get(task).isEmpty());
    }

    @Test
    void resumingWorkRetriesTheTasksParkedAtThePlayer() {
        RequestToken parked = pickup(near, 5, 0); // no courier yet: the player holds it
        assertEquals(PlayerResolver.ID, resolverOf(parked));
        DeliverymanJob job = courier(0);
        assertEquals(PlayerResolver.ID, resolverOf(parked));

        job.setWorking(colony, true);

        assertTrue(resolverOf(parked).startsWith("pickup:"));
        assertEquals(List.of(parked), warehouseQueue());
    }

    private String resolverOf(RequestToken token) {
        return m.resolverOf(token).map(Resolver::resolverId).orElse("none");
    }

    @Test
    void firedCourierFailsItsTasks() {
        DeliverymanJob job = courier(0);
        RequestToken task = delivery(near, 13, STONE);
        assertEquals(task, current(job));
        Building hut = colony.buildings().at(job.citizen().workBuilding()).orElseThrow();

        hut.module(WorkerModule.class)
                .orElseThrow()
                .fire(colony, hut, job.citizen().id());

        assertTrue(job.taskQueue().isEmpty());
        assertTrue(m.get(task).isEmpty());
    }

    @Test
    void courierWhoseHutIsGoneOnLoadFailsItsTasks() {
        DeliverymanJob job = courier(0);
        RequestToken task = delivery(near, 13, STONE);
        assertEquals(task, current(job));
        JsonObject json = ColonySerializer.write(colony);
        JsonArray buildings = json.getAsJsonArray("buildings");
        for (int i = buildings.size() - 1; i >= 0; i--) {
            if (buildings.get(i).getAsJsonObject().get("type").getAsString().equals(DeliverymanHut.TYPE.id())) {
                buildings.remove(i);
            }
        }

        Colony reloaded = ColonySerializer.read(json, t.context(), new TerritoryIndex());

        assertTrue(reloaded.citizens().get(1).orElseThrow().job().isEmpty());
        assertTrue(reloaded.requests().get(task).isEmpty());
    }

    @Test
    void queueAndOngoingDeliveriesSurviveSaveAndLoad() {
        DeliverymanJob job = courier(0);
        RequestToken task = delivery(near, 13, STONE);
        assertEquals(task, current(job));
        job.addConcurrentDelivery(task);
        job.setWorking(colony, true);

        DeliverymanJob loaded = new DeliverymanJob(job.citizen());
        loaded.read(job.write());

        assertEquals(List.of(task), loaded.taskQueue());
        assertEquals(Set.of(task), loaded.ongoingDeliveries());
        assertTrue(loaded.isWorking());
    }

    @Test
    void agedPrioritySurvivesSaveAndLoad() {
        DeliverymanJob job = courier(0);
        RequestToken farAway = delivery(far, 13, STONE);
        RequestToken chosen = delivery(near, 13, STONE);
        assertEquals(chosen, current(job));
        assertEquals(14, priority(farAway));

        Colony loaded = ColonySerializer.read(ColonySerializer.write(colony), t.context(), new TerritoryIndex());

        Requestable aged = loaded.requests().get(farAway).orElseThrow().requestable();
        assertEquals(14, ((Delivery) aged).priority());
    }
}
