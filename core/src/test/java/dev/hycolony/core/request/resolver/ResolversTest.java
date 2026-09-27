package dev.hycolony.core.request.resolver;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.hycolony.core.building.Building;
import dev.hycolony.core.building.BuildingTypes;
import dev.hycolony.core.kernel.BlockPos;
import dev.hycolony.core.kernel.item.ItemAmount;
import dev.hycolony.core.kernel.item.ItemKey;
import dev.hycolony.core.request.Request;
import dev.hycolony.core.request.RequestManager;
import dev.hycolony.core.request.Requester;
import dev.hycolony.core.request.Resolver;
import dev.hycolony.core.request.model.Delivery;
import dev.hycolony.core.request.model.RequestState;
import dev.hycolony.core.request.model.RequestToken;
import dev.hycolony.core.request.model.Requestable;
import dev.hycolony.core.request.model.RequesterId;
import dev.hycolony.core.request.model.StackRequest;
import dev.hycolony.core.testing.FakeCatalog;
import dev.hycolony.core.testing.FakeContainers;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class ResolversTest {
    private static final ItemKey PLANKS = new ItemKey("Wood_Planks");
    private static final BlockPos CENTER = new BlockPos(0, 64, 0);
    private final FakeContainers containers = new FakeContainers();
    private final Map<RequesterId, Requester> registry = new HashMap<>();
    private final RequestManager m = new RequestManager(id -> Optional.ofNullable(registry.get(id)), new FakeCatalog());
    private final RetryingResolver retrying = new RetryingResolver(CENTER);

    ResolversTest() {
        m.registerBuiltIn(new PlayerResolver(CENTER));
        m.registerBuiltIn(retrying);
    }

    private Building hut(BlockPos pos) {
        Building b = Building.create(BuildingTypes.TOWN_HALL, pos, 0);
        b.attachContainers(containers);
        registry.put(b.requesterId(), b);
        m.onProviderAdded(b);
        return b;
    }

    private void stock(Building b, int count) {
        containers
                .containers
                .computeIfAbsent(b.position(), p -> new HashMap<>())
                .put(PLANKS, count);
    }

    private String resolverOf(RequestToken t) {
        return m.resolverOf(t).map(Resolver::resolverId).orElse("none");
    }

    private static StackRequest planks(int count) {
        return new StackRequest(PLANKS, count, count, true);
    }

    @Test
    void buildingResolverResolvesFromOwnHutStockOnly() {
        Building a = hut(new BlockPos(0, 64, 0));
        Building b = hut(new BlockPos(50, 64, 0));
        stock(a, 10);

        RequestToken fromB = m.createAndAssign(b, planks(5), -1);
        assertEquals("retrying", resolverOf(fromB));

        RequestToken fromA = m.createAndAssign(a, planks(5), 2);
        Request r = m.get(fromA).orElseThrow();
        assertEquals("building:0,64,0", resolverOf(fromA));
        assertEquals(RequestState.COMPLETED, r.state());
        assertEquals(List.of(new ItemAmount(PLANKS, 5)), r.deliveries());
        assertEquals(10, containers.count(a.containers(), PLANKS), "items stay in the chest until picked up");

        RequestToken notByBuilding = m.createAndAssign(a, new StackRequest(PLANKS, 1, 1, false), -1);
        assertEquals("retrying", resolverOf(notByBuilding));
    }

    @Test
    void buildingResolverSubtractsReservedDeliveries() {
        Building a = hut(new BlockPos(0, 64, 0));
        stock(a, 10);
        RequestToken first = m.createAndAssign(a, planks(8), -1);
        assertEquals(
                List.of(new ItemAmount(PLANKS, 8)), m.get(first).orElseThrow().deliveries());

        RequestToken second = m.createAndAssign(a, planks(5), -1);
        assertEquals("retrying", resolverOf(second), "only 2 unreserved planks left");

        RequestToken third = m.createAndAssign(a, new StackRequest(PLANKS, 5, 2, true), -1);
        assertEquals(
                List.of(new ItemAmount(PLANKS, 2)), m.get(third).orElseThrow().deliveries());
    }

    @Test
    void retryingRetriesThreeTimesEvery1200TicksThenPlayer() {
        Building a = hut(new BlockPos(0, 64, 0));
        RequestToken t = m.createAndAssign(a, planks(5), -1);
        assertEquals("retrying", resolverOf(t));
        assertEquals(1, retrying.tries().get(t));

        int ticks = 0;
        while (resolverOf(t).equals("retrying")) {
            m.tick();
            ticks += RequestManager.TICK_INTERVAL;
            assertTrue(ticks < 10_000, "never reached the player");
        }
        assertEquals("player", resolverOf(t));
        // One 11-tick step of overshoot per 1200-tick delay.
        int expected = RetryingResolver.MAX_TRIES * RetryingResolver.DELAY_TICKS;
        assertTrue(
                ticks >= expected && ticks <= expected + RetryingResolver.MAX_TRIES * RequestManager.TICK_INTERVAL,
                "reached player after " + ticks + " ticks");

        m.tick();
        assertFalse(retrying.tries().containsKey(t), "forgotten once another resolver owns it");
        assertFalse(retrying.delays().containsKey(t));
    }

    @Test
    void dueRequestThatCannotBeReassignedKeepsAFreshDelay() {
        Building a = hut(new BlockPos(0, 64, 0));
        RequestToken t = m.createAndAssign(a, planks(5), -1);
        m.createChild(retrying, t, new StackRequest(PLANKS, 1, 1, false));
        for (int i = 0; i * RequestManager.TICK_INTERVAL < RetryingResolver.DELAY_TICKS; i++) {
            m.tick();
        }
        assertEquals("retrying", resolverOf(t));
        assertEquals(RetryingResolver.DELAY_TICKS, retrying.delays().get(t), "not stranded without a delay");
        assertEquals(1, retrying.tries().get(t));
    }

    @Test
    void colonyUpdateReassignsStuckRequestsToBuildingWhenStockArrives() {
        Building a = hut(new BlockPos(0, 64, 0));
        Building b = hut(new BlockPos(50, 64, 0));
        RequestToken t = m.createAndAssign(a, planks(5), -1);
        RequestToken other = m.createAndAssign(b, planks(5), -1);
        assertEquals("retrying", resolverOf(t));

        stock(a, 5);
        m.onColonyUpdate(r -> r.requester().equals(a.requesterId()));

        Request r = m.get(t).orElseThrow();
        assertEquals("building:0,64,0", resolverOf(t));
        assertEquals(RequestState.COMPLETED, r.state());
        assertEquals(List.of(new ItemAmount(PLANKS, 5)), r.deliveries());
        assertFalse(retrying.delays().containsKey(t));
        assertEquals("retrying", resolverOf(other), "not matched by the predicate");
    }

    @Test
    void playerResolverNeverTakesANonDeliverable() {
        Building a = hut(new BlockPos(0, 64, 0));
        stock(a, 5);
        Requestable notItems = new Delivery(a.position(), a.requesterId(), new ItemAmount(PLANKS, 5), 13);

        RequestToken t = m.createAndAssign(a, notItems, -1);

        assertFalse(new PlayerResolver(CENTER).handles(notItems));
        assertEquals("none", resolverOf(t), "no built-in resolver takes a non-deliverable");
        assertEquals(RequestState.REPORTED, m.get(t).orElseThrow().state());
    }
}
