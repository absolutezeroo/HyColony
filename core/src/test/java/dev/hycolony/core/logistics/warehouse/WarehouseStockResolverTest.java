package dev.hycolony.core.logistics.warehouse;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.hycolony.core.building.Building;
import dev.hycolony.core.building.BuildingTypes;
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
import dev.hycolony.core.request.model.RequestToken;
import dev.hycolony.core.request.model.Requestable;
import dev.hycolony.core.request.model.StackRequest;
import dev.hycolony.core.testing.TestContexts;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class WarehouseStockResolverTest {
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

    private void stock(BlockPos container, int count) {
        t.containers
                .containers
                .computeIfAbsent(container, p -> new LinkedHashMap<>())
                .merge(STONE, count, Integer::sum);
    }

    private RequestToken request(Building requester, int count, int minCount) {
        return m.createAndAssign(requester, new StackRequest(STONE, count, minCount, true), -1);
    }

    private static Resolver stockResolver(Building warehouse) {
        return warehouse.resolvers().stream()
                .filter(r -> r.priority() == WarehouseStockResolver.PRIORITY)
                .findFirst()
                .orElseThrow();
    }

    private String resolverOf(RequestToken token) {
        return m.resolverOf(token).map(Resolver::resolverId).orElse("none");
    }

    private List<Requestable> children(RequestToken token) {
        return m.get(token).orElseThrow().children().stream()
                .map(c -> m.get(c).orElseThrow().requestable())
                .toList();
    }

    @Test
    void neverServesItself() {
        Building w = warehouse(new BlockPos(10, 64, 0));
        stock(w.position(), 10);

        RequestToken own = m.createAndAssign(w, new StackRequest(STONE, 5, 5, false), -1);

        assertNotEquals(stockResolver(w).resolverId(), resolverOf(own));
    }

    @Test
    void resolvesWithEnoughStockAcrossWarehouses() {
        Building near = warehouse(new BlockPos(10, 64, 0));
        Building far = warehouse(new BlockPos(90, 64, 0));
        stock(near.position(), 4);
        stock(far.position(), 4);
        Building hut = hut(new BlockPos(0, 64, 0));

        RequestToken token = request(hut, 8, 8);

        assertEquals(stockResolver(near).resolverId(), resolverOf(token));
    }

    @Test
    void notEnoughStockInAnyWarehouseLeavesTheRequestToOthers() {
        Building w = warehouse(new BlockPos(10, 64, 0));
        stock(w.position(), 4);
        Building hut = hut(new BlockPos(0, 64, 0));

        RequestToken token = request(hut, 8, 8);

        assertNotEquals(stockResolver(w).resolverId(), resolverOf(token));
    }

    @Test
    void partialStockCreatesAChildForTheMissingCount() {
        Building near = warehouse(new BlockPos(10, 64, 0));
        Building far = warehouse(new BlockPos(90, 64, 0));
        stock(near.position(), 4);
        stock(far.position(), 6);
        Building hut = hut(new BlockPos(0, 64, 0));

        RequestToken token = request(hut, 8, 8);

        Request parent = m.get(token).orElseThrow();
        assertEquals(1, parent.children().size());
        Request child = m.get(parent.children().get(0)).orElseThrow();
        assertEquals(new StackRequest(STONE, 4, 8, true), child.requestable());
        assertEquals(stockResolver(near).requesterId(), child.requester());
        assertEquals(stockResolver(far).resolverId(), resolverOf(child.token()));
    }

    @Test
    void partialStockAboveMinCountResolvesWithoutChild() {
        Building w = warehouse(new BlockPos(10, 64, 0));
        stock(w.position(), 5);
        Building hut = hut(new BlockPos(0, 64, 0));

        RequestToken token = request(hut, 8, 4);

        assertEquals(stockResolver(w).resolverId(), resolverOf(token));
        assertEquals(
                List.of(new Delivery(
                        w.position(), hut.requesterId(), new ItemAmount(STONE, 5), Delivery.DEFAULT_DELIVERY_PRIORITY)),
                children(token));
        assertEquals(5, ((Delivery) children(token).get(0)).stack().count());
        assertEquals(
                List.of(new ItemAmount(STONE, 5)), m.get(token).orElseThrow().deliveries());
    }

    @Test
    void oneDeliveryPerSourceSlot() {
        t.containers.maxStack = 64;
        Building w = warehouse(new BlockPos(10, 64, 0));
        BlockPos rack = new BlockPos(12, 64, 0);
        w.addContainer(rack);
        t.containers.slots.put(w.position(), 9);
        t.containers.slots.put(rack, 9);
        stock(w.position(), 100);
        stock(rack, 50);
        BlockPos untouched = new BlockPos(14, 64, 0);
        w.addContainer(untouched);
        stock(untouched, 30);
        Building hut = hut(new BlockPos(0, 64, 0));

        RequestToken token = request(hut, 120, 120);

        int p = Delivery.DEFAULT_DELIVERY_PRIORITY;
        assertEquals(
                List.of(
                        new Delivery(w.position(), hut.requesterId(), new ItemAmount(STONE, 64), p),
                        new Delivery(w.position(), hut.requesterId(), new ItemAmount(STONE, 36), p),
                        new Delivery(rack, hut.requesterId(), new ItemAmount(STONE, 20), p)),
                children(token));
        assertEquals(
                List.of(64, 36, 20),
                children(token).stream()
                        .map(d -> ((Delivery) d).stack().count())
                        .toList());
        assertEquals(
                List.of(new ItemAmount(STONE, 64), new ItemAmount(STONE, 36), new ItemAmount(STONE, 20)),
                m.get(token).orElseThrow().deliveries());
        for (RequestToken c : m.get(token).orElseThrow().children()) {
            assertEquals(stockResolver(w).requesterId(), m.get(c).orElseThrow().requester());
        }
    }

    @Test
    void suitabilityIsDistanceOverTenPlusQueue() {
        Building w = warehouse(new BlockPos(0, 64, 0));
        stock(w.position(), 10);
        WarehouseRequestQueue queue = w.module(WarehouseRequestQueue.class).orElseThrow();
        queue.add(RequestToken.random());
        queue.add(RequestToken.random());
        Building far = hut(new BlockPos(35, 64, 0));
        Building near = hut(new BlockPos(5, 64, 0));

        Request fromFar = m.get(request(far, 1, 1)).orElseThrow();
        Request fromNear = m.get(request(near, 1, 1)).orElseThrow();

        assertEquals(3 + 2, stockResolver(w).suitability(m, fromFar));
        assertEquals(1 + 2, stockResolver(w).suitability(m, fromNear));
    }

    @Test
    void resolvesEvenWithoutCouriersLikeMc() {
        Building w = warehouse(new BlockPos(10, 64, 0));
        stock(w.position(), 10);
        Building hut = hut(new BlockPos(0, 64, 0));
        assertTrue(
                w.module(CourierAssignmentModule.class).orElseThrow().couriers().isEmpty());

        RequestToken token = request(hut, 5, 5);

        assertEquals(stockResolver(w).resolverId(), resolverOf(token));
        assertEquals(1, children(token).size());
    }
}
