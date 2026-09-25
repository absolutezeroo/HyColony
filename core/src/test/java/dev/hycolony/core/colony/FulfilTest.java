package dev.hycolony.core.colony;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.hycolony.core.building.Building;
import dev.hycolony.core.citizen.CitizenData;
import dev.hycolony.core.kernel.BlockPos;
import dev.hycolony.core.kernel.item.ItemAmount;
import dev.hycolony.core.kernel.item.ItemKey;
import dev.hycolony.core.request.Request;
import dev.hycolony.core.request.RequestState;
import dev.hycolony.core.request.RequestToken;
import dev.hycolony.core.request.Resolver;
import dev.hycolony.core.request.StackRequest;
import dev.hycolony.core.testing.TestContexts;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class FulfilTest {
    private static final ItemKey PLANKS = new ItemKey("Wood_Planks");
    private final TestContexts t = new TestContexts();
    private final ColonyManager manager = new ColonyManager(t.context());
    private final UUID alice = UUID.randomUUID();
    private final UUID bob = UUID.randomUUID();
    private final BlockPos hall = new BlockPos(0, 64, 0);
    private final Colony colony;
    private final Building hut;
    private final CitizenData citizen = new CitizenData(1);

    FulfilTest() {
        manager.beginFoundation(alice, "Alice", hall, 0);
        colony = manager.confirmFoundation(alice, "A").orElseThrow();
        hut = colony.buildings().at(hall).orElseThrow();
        colony.citizens().restore(citizen);
    }

    private RequestToken request(int count, int citizenId) {
        RequestToken token = colony.requests().createAndAssign(hut, new StackRequest(PLANKS, count, count, true), citizenId);
        assertEquals("retrying", colony.requests().resolverOf(token).map(Resolver::resolverId).orElseThrow());
        return token;
    }

    private Request get(RequestToken token) {
        return colony.requests().get(token).orElseThrow();
    }

    @Test
    void fulfilMovesItemsToCitizenAndOverrules() {
        RequestToken token = request(10, 1);
        t.playerInventory.give(alice, new ItemAmount(PLANKS, 15));

        assertTrue(manager.fulfil(alice, colony.id(), token));

        assertEquals(10, citizen.inventory().count(PLANKS));
        assertEquals(5, t.playerInventory.count(alice, PLANKS));
        assertEquals(RequestState.COMPLETED, get(token).state());
        assertEquals(List.of(new ItemAmount(PLANKS, 10)), get(token).deliveries());
    }

    @Test
    void partialFulfilClosesAndBuilderRerequests() {
        RequestToken token = request(10, 1);
        t.playerInventory.give(alice, new ItemAmount(PLANKS, 4));

        assertTrue(manager.fulfil(alice, colony.id(), token));

        assertEquals(4, citizen.inventory().count(PLANKS));
        assertEquals(0, t.playerInventory.count(alice, PLANKS));
        assertEquals(RequestState.COMPLETED, get(token).state(), "overruled and closed despite the partial amount");
        assertEquals(List.of(new ItemAmount(PLANKS, 4)), get(token).deliveries());
    }

    @Test
    void fulfilForTheBuildingGoesToTheHutContainers() {
        RequestToken token = request(3, -1);
        t.playerInventory.give(alice, new ItemAmount(PLANKS, 3));

        assertTrue(manager.fulfil(alice, colony.id(), token));

        assertEquals(3, t.containers.count(hut.containers(), PLANKS));
        assertEquals(RequestState.COMPLETED, get(token).state());
    }

    @Test
    void fulfilWithoutItemsReturnsFalse() {
        RequestToken token = request(10, 1);
        assertFalse(manager.fulfil(alice, colony.id(), token));
        assertEquals(RequestState.IN_PROGRESS, get(token).state());

        t.playerInventory.give(bob, new ItemAmount(PLANKS, 10));
        assertFalse(manager.fulfil(bob, colony.id(), token), "bob may not access the huts");
        assertEquals(10, t.playerInventory.count(bob, PLANKS));
        assertEquals(RequestState.IN_PROGRESS, get(token).state());
    }

    @Test
    void addToHutMovesAndOverrulesNextOpen() {
        RequestToken token = request(10, 1);
        t.playerInventory.give(alice, new ItemAmount(PLANKS, 6));

        assertEquals(6, manager.addToHut(alice, hall, PLANKS, 10));

        assertEquals(6, t.containers.count(hut.containers(), PLANKS));
        assertEquals(0, t.playerInventory.count(alice, PLANKS));
        assertEquals(RequestState.COMPLETED, get(token).state());
        assertEquals(List.of(new ItemAmount(PLANKS, 6)), get(token).deliveries());
    }

    @Test
    void requestsAndContainersSurviveColonySave() {
        RequestToken token = request(10, 1);
        BlockPos chest = new BlockPos(2, 64, 0);
        hut.addContainer(chest);

        Colony loaded = ColonySerializer.read(ColonySerializer.write(colony), t.context(), new TerritoryIndex());

        Building hut2 = loaded.buildings().at(hall).orElseThrow();
        assertEquals(List.of(hall, chest), hut2.containers());
        assertEquals("retrying", loaded.requests().resolverOf(token).map(Resolver::resolverId).orElseThrow());
        assertEquals(RequestState.IN_PROGRESS, loaded.requests().get(token).orElseThrow().state());
    }

    @Test
    void containerChangeReassignsStuckRequests() {
        RequestToken token = request(5, 1);
        t.containers.insert(hut.containers(), new ItemAmount(PLANKS, 5));

        manager.onContainerChanged(hall);

        assertEquals(RequestState.COMPLETED, get(token).state());
        assertEquals("building:0,64,0", colony.requests().resolverOf(token).map(Resolver::resolverId).orElseThrow());
    }
}
