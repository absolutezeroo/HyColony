package dev.hycolony.core.logistics.warehouse;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.hycolony.core.building.Building;
import dev.hycolony.core.building.BuildingTypes;
import dev.hycolony.core.citizen.CitizenData;
import dev.hycolony.core.colony.Colony;
import dev.hycolony.core.colony.permission.Permissions;
import dev.hycolony.core.colony.territory.TerritoryIndex;
import dev.hycolony.core.kernel.BlockPos;
import dev.hycolony.core.kernel.item.ItemAmount;
import dev.hycolony.core.kernel.item.ItemKey;
import dev.hycolony.core.request.Request;
import dev.hycolony.core.request.RequestManager;
import dev.hycolony.core.request.Resolver;
import dev.hycolony.core.request.model.Delivery;
import dev.hycolony.core.request.model.Pickup;
import dev.hycolony.core.request.model.RequestState;
import dev.hycolony.core.request.model.RequestToken;
import dev.hycolony.core.request.model.StackRequest;
import dev.hycolony.core.request.resolver.PlayerResolver;
import dev.hycolony.core.testing.TestContexts;
import dev.hycolony.core.testing.TestJobs;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class CourierResolverTest {
    private static final ItemKey STONE = new ItemKey("Rock_Stone");
    private final TestContexts t = new TestContexts();
    private final Colony colony = new Colony(
            t.context(),
            new TerritoryIndex(),
            new Colony.Founding(1, "T", new BlockPos(0, 64, 0), Permissions.createDefault(UUID.randomUUID(), "A")));
    private final RequestManager m = colony.requests();

    private Building warehouse(BlockPos pos) {
        Building b = Building.create(WarehouseBuilding.TYPE, pos, 0);
        b.setLevel(1);
        b.setBuilt(true);
        colony.buildings().add(b);
        return b;
    }

    private Building hut(BlockPos pos) {
        Building b = Building.create(BuildingTypes.TOWN_HALL, pos, 0);
        colony.buildings().add(b);
        return b;
    }

    /** A courier attached to the one warehouse that has room (MC CourierAssignmentModule.onColonyTick). */
    private TestJobs.TestCourierJob courier(int id) {
        CitizenData c = new CitizenData(id);
        TestJobs.TestCourierJob job =
                (TestJobs.TestCourierJob) TestJobs.COURIER.factory().apply(c);
        c.setJob(job);
        colony.citizens().restore(c);
        colony.buildings().onColonyTick(colony);
        return job;
    }

    private static List<RequestToken> queue(Building warehouse) {
        return warehouse.module(WarehouseRequestQueue.class).orElseThrow().tokens();
    }

    private String resolverOf(RequestToken token) {
        return m.resolverOf(token).map(Resolver::resolverId).orElse("none");
    }

    private static Resolver resolver(Building warehouse, String prefix) {
        return warehouse.resolvers().stream()
                .filter(r -> r.resolverId().startsWith(prefix))
                .findFirst()
                .orElseThrow();
    }

    private RequestToken pickup(Building from) {
        return m.createAndAssign(from, new Pickup(5, 0, 10), -1);
    }

    private RequestToken stoneFromWarehouse(Building w, Building hut) {
        t.containers
                .containers
                .computeIfAbsent(w.position(), p -> new LinkedHashMap<>())
                .merge(STONE, 10, Integer::sum);
        return m.createAndAssign(hut, new StackRequest(STONE, 5, 5, true), -1);
    }

    private RequestToken onlyChild(RequestToken parent) {
        List<RequestToken> children = m.get(parent).orElseThrow().children();
        assertEquals(1, children.size());
        return children.get(0);
    }

    @Test
    void noCourierNoResolve() {
        Building w = warehouse(new BlockPos(10, 64, 0));
        Building hut = hut(new BlockPos(0, 64, 0));

        RequestToken pickup = pickup(hut);
        RequestToken delivery = onlyChild(stoneFromWarehouse(w, hut));

        // MC: the player resolver's type is IRequestable, so it takes what no courier can carry.
        assertEquals(PlayerResolver.ID, resolverOf(pickup));
        assertEquals(PlayerResolver.ID, resolverOf(delivery));
        assertTrue(queue(w).isEmpty());
    }

    @Test
    void resolveQueuesAtTheWarehouse() {
        Building w = warehouse(new BlockPos(10, 64, 0));
        Building hut = hut(new BlockPos(0, 64, 0));
        courier(1);

        RequestToken delivery = onlyChild(stoneFromWarehouse(w, hut));
        RequestToken pickup = pickup(hut);

        assertEquals(resolver(w, "delivery:").resolverId(), resolverOf(delivery));
        assertEquals(resolver(w, "pickup:").resolverId(), resolverOf(pickup));
        assertEquals(List.of(delivery, pickup), queue(w));
        assertEquals(RequestState.IN_PROGRESS, m.get(delivery).orElseThrow().state());
        assertEquals(RequestState.IN_PROGRESS, m.get(pickup).orElseThrow().state());
    }

    @Test
    void anotherWarehousesDeliveryIsRefused() {
        Building near = warehouse(new BlockPos(10, 64, 0));
        Building other = warehouse(new BlockPos(90, 64, 0));
        Building hut = hut(new BlockPos(0, 64, 0));
        courier(1); // attached to one warehouse only
        Building served = queueOwnerOf(near, other);
        Building empty = served == near ? other : near;

        RequestToken delivery = onlyChild(stoneFromWarehouse(empty, hut));

        assertEquals(PlayerResolver.ID, resolverOf(delivery));
        assertFalse(queue(served).contains(delivery));
    }

    private static Building queueOwnerOf(Building a, Building b) {
        return a.module(CourierAssignmentModule.class).orElseThrow().couriers().isEmpty() ? b : a;
    }

    @Test
    void suitabilityIsQueueForDeliveriesAndDistanceForPickups() {
        Building w = warehouse(new BlockPos(0, 64, 0));
        Building far = hut(new BlockPos(35, 64, 0));
        courier(1);
        queue(w).add(RequestToken.random());
        queue(w).add(RequestToken.random());

        Request pickup = m.get(pickup(far)).orElseThrow();
        Request delivery = m.get(onlyChild(stoneFromWarehouse(w, far))).orElseThrow();

        assertEquals(35, resolver(w, "pickup:").suitability(m, pickup));
        // The delivery's requester is the warehouse's stock resolver: distance 0, so 1 + the queue, which now holds
        // the two placeholders, the pickup and the delivery itself.
        assertEquals(1 + 4, resolver(w, "delivery:").suitability(m, delivery));
    }

    @Test
    void pickupGoesToTheNearestWarehouse() {
        Building far = warehouse(new BlockPos(90, 64, 0));
        Building near = warehouse(new BlockPos(10, 64, 0));
        Building hut = hut(new BlockPos(0, 64, 0));
        courier(1);
        courier(2);
        courier(3);
        courier(4);

        RequestToken pickup = pickup(hut);

        assertEquals(resolver(near, "pickup:").resolverId(), resolverOf(pickup));
        assertTrue(queue(far).isEmpty());
    }

    @Test
    void cancelRemovesFromBothQueues() {
        Building w = warehouse(new BlockPos(10, 64, 0));
        Building hut = hut(new BlockPos(0, 64, 0));
        TestJobs.TestCourierJob courier = courier(1);
        RequestToken pickup = pickup(hut);
        courier.tasks.add(pickup); // what the courier job does when it takes the task (Task 7)

        m.updateState(pickup, RequestState.CANCELLED);

        assertTrue(queue(w).isEmpty());
        assertTrue(courier.tasks.isEmpty());
    }

    @Test
    void failedDeliveryReassignsTheParent() {
        Building w = warehouse(new BlockPos(10, 64, 0));
        Building hut = hut(new BlockPos(0, 64, 0));
        TestJobs.TestCourierJob courier = courier(1);
        RequestToken parent = stoneFromWarehouse(w, hut);
        RequestToken failed = onlyChild(parent);
        courier.tasks.add(failed);

        m.updateState(failed, RequestState.FAILED);

        assertTrue(m.get(failed).isEmpty());
        assertTrue(courier.tasks.isEmpty());
        RequestToken retry = onlyChild(parent);
        assertNotEquals(failed, retry);
        assertEquals(List.of(retry), queue(w));
        assertEquals(
                new ItemAmount(STONE, 5), ((Delivery) m.get(retry).orElseThrow().requestable()).stack());
    }
}
