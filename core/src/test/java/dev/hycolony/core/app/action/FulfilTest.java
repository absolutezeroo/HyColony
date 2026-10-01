package dev.hycolony.core.app.action;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.JsonObject;
import dev.hycolony.core.app.ColonyManager;
import dev.hycolony.core.app.persistence.ColonySerializer;
import dev.hycolony.core.building.Building;
import dev.hycolony.core.citizen.CitizenData;
import dev.hycolony.core.colony.Colony;
import dev.hycolony.core.colony.ColonyState;
import dev.hycolony.core.colony.permission.Permissions;
import dev.hycolony.core.colony.territory.TerritoryIndex;
import dev.hycolony.core.kernel.BlockPos;
import dev.hycolony.core.kernel.item.ItemAmount;
import dev.hycolony.core.kernel.item.ItemKey;
import dev.hycolony.core.kernel.item.ToolInfo;
import dev.hycolony.core.kernel.item.ToolType;
import dev.hycolony.core.request.Request;
import dev.hycolony.core.request.Resolver;
import dev.hycolony.core.request.model.RequestState;
import dev.hycolony.core.request.model.RequestToken;
import dev.hycolony.core.request.model.StackRequest;
import dev.hycolony.core.request.model.ToolRequest;
import dev.hycolony.core.request.resolver.PlayerResolver;
import dev.hycolony.core.request.resolver.RetryingResolver;
import dev.hycolony.core.testing.TestContexts;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class FulfilTest {
    private static final ItemKey PLANKS = new ItemKey("Wood_Planks");
    private final TestContexts t = new TestContexts();
    private final ColonyManager manager = t.manager();
    private final UUID alice = UUID.randomUUID();
    private final UUID bob = UUID.randomUUID();
    private final BlockPos hall = new BlockPos(0, 64, 0);
    private final Colony colony;
    private final Building hut;
    private final CitizenData citizen = new CitizenData(1);

    FulfilTest() {
        manager.foundation().begin(alice, "Alice", hall, 0);
        colony = manager.foundation().confirm(alice, "A").orElseThrow();
        hut = colony.buildings().at(hall).orElseThrow();
        colony.citizens().restore(citizen);
    }

    private RequestToken request(int count, int citizenId) {
        RequestToken token =
                colony.requests().createAndAssign(hut, new StackRequest(PLANKS, count, count, true), citizenId);
        assertEquals(
                "retrying",
                colony.requests().resolverOf(token).map(Resolver::resolverId).orElseThrow());
        return token;
    }

    private Request get(RequestToken token) {
        return colony.requests().get(token).orElseThrow();
    }

    @Test
    void aWornToolAPlayerSuppliesKeepsItsWear() {
        ItemKey pick = new ItemKey("Tool_Pickaxe_Iron");
        t.catalog.maxStacks.put(pick, 1);
        RequestToken token = colony.requests().createAndAssign(hut, new StackRequest(pick, 1, 1, true), citizen.id());
        t.playerInventory.give(alice, new ItemAmount(pick, 1, 30));

        assertTrue(manager.requestActions().fulfil(alice, colony.id(), token));

        assertEquals(List.of(new ItemAmount(pick, 1, 30)), citizen.inventory().contents());
        assertEquals(0, t.playerInventory.count(alice, pick));
    }

    /** MC: a broken tool no longer exists; the player's good shovel goes, the broken one stays with them. */
    @Test
    void aPlayerNeverSuppliesABrokenToolForAToolRequest() {
        ItemKey shovel = new ItemKey("Tool_Shovel_Crude");
        t.catalog.tools.put(shovel, new ToolInfo(ToolType.SHOVEL, 0, 1f));
        t.catalog.durability.put(shovel, 150);
        t.catalog.maxStacks.put(shovel, 1);
        RequestToken token =
                colony.requests().createAndAssign(hut, new ToolRequest(ToolType.SHOVEL, 0, 5), citizen.id());
        t.playerInventory.give(alice, new ItemAmount(shovel, 1, 150));
        t.playerInventory.give(alice, new ItemAmount(shovel, 1, 20));

        assertTrue(manager.requestActions().fulfil(alice, colony.id(), token));

        assertEquals(List.of(new ItemAmount(shovel, 1, 20)), citizen.inventory().contents());
        assertEquals(List.of(new ItemAmount(shovel, 1, 150)), t.playerInventory.worn.get(alice));
    }

    @Test
    void aWornToolAPlayerAddsToAHutKeepsItsWear() {
        ItemKey pick = new ItemKey("Tool_Pickaxe_Iron");
        t.playerInventory.give(alice, new ItemAmount(pick, 1, 30));

        assertEquals(1, manager.requestActions().addToHut(alice, hall, pick, 1));

        assertTrue(
                hut.containers().stream().anyMatch(c -> t.containers.stacks(c).contains(new ItemAmount(pick, 1, 30))));
    }

    @Test
    void fulfilMovesItemsToCitizenAndOverrules() {
        RequestToken token = request(10, 1);
        t.playerInventory.give(alice, new ItemAmount(PLANKS, 15));

        assertTrue(manager.requestActions().fulfil(alice, colony.id(), token));

        assertEquals(10, citizen.inventory().count(PLANKS));
        assertEquals(5, t.playerInventory.count(alice, PLANKS));
        assertEquals(RequestState.COMPLETED, get(token).state());
        assertEquals(List.of(new ItemAmount(PLANKS, 10)), get(token).deliveries());
    }

    @Test
    void partialFulfilClosesAndBuilderRerequests() {
        RequestToken token = request(10, 1);
        t.playerInventory.give(alice, new ItemAmount(PLANKS, 4));

        assertTrue(manager.requestActions().fulfil(alice, colony.id(), token));

        assertEquals(4, citizen.inventory().count(PLANKS));
        assertEquals(0, t.playerInventory.count(alice, PLANKS));
        assertEquals(RequestState.COMPLETED, get(token).state(), "overruled and closed despite the partial amount");
        assertEquals(List.of(new ItemAmount(PLANKS, 4)), get(token).deliveries());
    }

    @Test
    void fulfilForTheBuildingGoesToTheHutContainers() {
        RequestToken token = request(3, -1);
        t.playerInventory.give(alice, new ItemAmount(PLANKS, 3));

        assertTrue(manager.requestActions().fulfil(alice, colony.id(), token));

        assertEquals(3, t.containers.count(hut.containers(), PLANKS));
        assertEquals(RequestState.COMPLETED, get(token).state());
    }

    @Test
    void aFriendMayNotFulfilAndIsToldAsMc() {
        UUID carol = UUID.randomUUID();
        assertTrue(manager.administration().setRank(alice, colony.id(), carol, "Carol", Permissions.FRIEND));
        RequestToken token = request(10, 1);
        t.playerInventory.give(carol, new ItemAmount(PLANKS, 10));

        assertFalse(manager.requestActions().fulfil(carol, colony.id(), token), "MC: MANAGE_HUTS");
        assertEquals(10, t.playerInventory.count(carol, PLANKS));
        assertEquals(
                "hycolony.permission.toolDenied",
                t.notifier.sent.getLast().msg().key(),
                "MC AbstractColonyServerMessage tells the refusal");
    }

    @Test
    void fulfilWithoutItemsReturnsFalse() {
        RequestToken token = request(10, 1);
        assertFalse(manager.requestActions().fulfil(alice, colony.id(), token));
        assertEquals(RequestState.IN_PROGRESS, get(token).state());

        t.playerInventory.give(bob, new ItemAmount(PLANKS, 10));
        assertFalse(manager.requestActions().fulfil(bob, colony.id(), token), "bob may not access the huts");
        assertEquals(10, t.playerInventory.count(bob, PLANKS));
        assertEquals(RequestState.IN_PROGRESS, get(token).state());
    }

    @Test
    void addToHutMovesAndOverrulesNextOpen() {
        RequestToken token = request(10, 1);
        t.playerInventory.give(alice, new ItemAmount(PLANKS, 6));

        assertEquals(6, manager.requestActions().addToHut(alice, hall, PLANKS, 10));

        assertEquals(6, t.containers.count(hut.containers(), PLANKS));
        assertEquals(0, t.playerInventory.count(alice, PLANKS));
        assertEquals(RequestState.COMPLETED, get(token).state());
        assertEquals(List.of(new ItemAmount(PLANKS, 6)), get(token).deliveries());
    }

    @Test
    void addToHutShowsTheHutAgain() {
        t.playerInventory.give(alice, new ItemAmount(PLANKS, 1));
        t.ui.shown.clear();

        manager.requestActions().addToHut(alice, hall, PLANKS, 1);

        assertTrue(t.ui.shown.containsKey(alice), "the player sees what the hut now holds");
    }

    /** MC: a broken tool no longer exists, so adding one to the hut closes no tool request. */
    @Test
    void addingABrokenToolToTheHutNeverClosesAToolRequest() {
        ItemKey shovel = new ItemKey("Tool_Shovel_Crude");
        t.catalog.tools.put(shovel, new ToolInfo(ToolType.SHOVEL, 0, 1f));
        t.catalog.durability.put(shovel, 150);
        t.catalog.maxStacks.put(shovel, 1);
        RequestToken token = colony.requests().createAndAssign(hut, new ToolRequest(ToolType.SHOVEL, 0, 5), -1);
        t.playerInventory.give(alice, new ItemAmount(shovel, 1, 150));

        assertEquals(1, manager.requestActions().addToHut(alice, hall, shovel, 1));

        assertTrue(get(token).state().isBefore(RequestState.COMPLETED));
        t.playerInventory.give(alice, new ItemAmount(shovel, 1, 20));
        assertEquals(1, manager.requestActions().addToHut(alice, hall, shovel, 1));
        assertEquals(RequestState.COMPLETED, get(token).state());
    }

    @Test
    void requestsAndContainersSurviveColonySave() {
        RequestToken token = request(10, 1);
        BlockPos chest = new BlockPos(2, 64, 0);
        hut.registeredBlocks().addContainer(chest);

        Colony loaded = ColonySerializer.read(ColonySerializer.write(colony), t.context(), new TerritoryIndex());

        Building hut2 = loaded.buildings().at(hall).orElseThrow();
        assertEquals(List.of(chest, hall), hut2.containers());
        assertEquals(
                "retrying",
                loaded.requests().resolverOf(token).map(Resolver::resolverId).orElseThrow());
        assertEquals(
                RequestState.IN_PROGRESS,
                loaded.requests().get(token).orElseThrow().state());
    }

    @Test
    void containerChangeReassignsStuckRequests() {
        RequestToken token = request(5, 1);
        t.containers.insert(hut.containers(), new ItemAmount(PLANKS, 5));

        manager.requestActions().onContainerChanged(hall);

        assertEquals(RequestState.COMPLETED, get(token).state());
        assertEquals(
                "building:0,64,0",
                colony.requests().resolverOf(token).map(Resolver::resolverId).orElseThrow());
    }

    private RetryingResolver retrying() {
        return colony.requests()
                .resolver(RetryingResolver.ID)
                .map(RetryingResolver.class::cast)
                .orElseThrow();
    }

    @Test
    void addToHutThenNewRequestForSameItemIsNotDoubleReserved() {
        RequestToken a = request(10, 1);
        t.playerInventory.give(alice, new ItemAmount(PLANKS, 6));
        assertEquals(6, manager.requestActions().addToHut(alice, hall, PLANKS, 10));
        assertEquals(RequestState.COMPLETED, get(a).state());

        request(4, 1); // asserts it went to retrying: the 6 planks in the hut are A's
    }

    @Test
    void removingBuildingCancelsItsRequestsAndNothingPersists() {
        RequestToken retried = request(10, 1);
        RequestToken atPlayer = request(3, 1);
        colony.requests().onColonyUpdate(r -> r.token().equals(atPlayer));
        t.containers.insert(hut.containers(), new ItemAmount(PLANKS, 2));
        RequestToken byHut = colony.requests().createAndAssign(hut, new StackRequest(PLANKS, 2, 2, true), -1);
        assertEquals(RequestState.COMPLETED, get(byHut).state());

        manager.huts().onRemoved(hall, UUID.randomUUID());

        assertTrue(colony.requests().all().isEmpty());
        assertTrue(retrying().delays().isEmpty());
        PlayerResolver player = colony.requests()
                .resolver(PlayerResolver.ID)
                .map(PlayerResolver.class::cast)
                .orElseThrow();
        assertTrue(player.open().isEmpty());
        JsonObject saved = ColonySerializer.write(colony).getAsJsonObject("requests");
        assertEquals(0, saved.getAsJsonArray("requests").size());
        assertEquals(0, saved.getAsJsonObject("assignments").size());
        assertFalse(colony.requests().get(retried).isPresent());
    }

    @Test
    void fulfilWithFullCitizenInventoryGivesBackRest() {
        ItemKey dirt = new ItemKey("Dirt");
        t.catalog.maxStacks.put(PLANKS, 5);
        for (int i = 0; i < CitizenData.INVENTORY_SLOTS - 1; i++) {
            citizen.inventory().insert(new ItemAmount(dirt, 64), k -> 64);
        }
        RequestToken token = request(10, 1);
        t.playerInventory.give(alice, new ItemAmount(PLANKS, 10));

        assertTrue(manager.requestActions().fulfil(alice, colony.id(), token));

        assertEquals(5, citizen.inventory().count(PLANKS));
        assertEquals(5, t.playerInventory.count(alice, PLANKS), "the rest goes back to the player");
        assertEquals(List.of(new ItemAmount(PLANKS, 5)), get(token).deliveries());
    }

    @Test
    void fulfilToolRequestUsesFirstMatchingTool() {
        ItemKey axe = new ItemKey("Axe");
        ItemKey woodPick = new ItemKey("Pick_Wood");
        ItemKey stonePick = new ItemKey("Pick_Stone");
        ItemKey ironPick = new ItemKey("Pick_Iron");
        t.catalog.tools.put(axe, new ToolInfo(ToolType.AXE, 1, 1f));
        t.catalog.tools.put(woodPick, new ToolInfo(ToolType.PICKAXE, 0, 1f));
        t.catalog.tools.put(stonePick, new ToolInfo(ToolType.PICKAXE, 1, 1f));
        t.catalog.tools.put(ironPick, new ToolInfo(ToolType.PICKAXE, 2, 1f));
        for (ItemKey k : List.of(axe, woodPick, stonePick, ironPick)) {
            t.playerInventory.give(alice, new ItemAmount(k, 1));
        }
        RequestToken token = colony.requests().createAndAssign(hut, new ToolRequest(ToolType.PICKAXE, 1, 3), 1);

        assertTrue(manager.requestActions().fulfil(alice, colony.id(), token));

        assertEquals(1, citizen.inventory().count(stonePick));
        assertEquals(1, t.playerInventory.count(alice, ironPick));
        assertEquals(1, t.playerInventory.count(alice, woodPick));
        assertEquals(List.of(new ItemAmount(stonePick, 1)), get(token).deliveries());
    }

    @Test
    void addToHutRequiresPermission() {
        RequestToken token = request(10, 1);
        t.playerInventory.give(bob, new ItemAmount(PLANKS, 6));

        assertEquals(0, manager.requestActions().addToHut(bob, hall, PLANKS, 10));

        assertEquals(6, t.playerInventory.count(bob, PLANKS));
        assertEquals(0, t.containers.count(hut.containers(), PLANKS));
        assertEquals(RequestState.IN_PROGRESS, get(token).state());
    }

    private void run(int ticks) {
        for (int i = 0; i < ticks; i++) {
            t.clock.tick++;
            colony.tick();
        }
    }

    @Test
    void colonyTicksRequestsEvery11TicksWhenActive() {
        RequestToken token = request(10, 1);
        run(200);
        assertEquals(ColonyState.INACTIVE, colony.state());
        assertEquals(RetryingResolver.DELAY_UPDATES, retrying().delays().get(token), "not ticked while inactive");

        t.players.online.put(alice, hall);
        run(200);
        assertEquals(ColonyState.ACTIVE, colony.state());
        int before = retrying().delays().get(token);
        run(110);
        int updates = before - retrying().delays().get(token);
        // 10 request updates in 110 game ticks; a state-update tick may delay one of them.
        assertTrue(updates == 9 || updates == 10, "updates " + updates);
    }

    @Test
    void aHutWhoseResolversAreNotAttachedYetServesNothing() {
        Request r = colony.requests().get(request(5, 1)).orElseThrow();
        Building loose = Building.create(hut.type(), new BlockPos(500, 64, 500), 0); // never added to the colony

        assertFalse(loose.stockCanServe(colony.requests(), r));
    }

    @Test
    void containerChangeReassignsOnlyWhatTheHutCanNowServe() {
        RequestToken planks = request(5, 1);
        RequestToken stone =
                colony.requests().createAndAssign(hut, new StackRequest(new ItemKey("Stone"), 3, 3, true), 1);
        t.containers.insert(hut.containers(), new ItemAmount(new ItemKey("Dirt"), 10));

        manager.requestActions().onContainerChanged(hall);

        assertEquals(
                "retrying",
                colony.requests().resolverOf(planks).map(Resolver::resolverId).orElseThrow());
        assertEquals(
                "retrying",
                colony.requests().resolverOf(stone).map(Resolver::resolverId).orElseThrow());

        t.containers.insert(hut.containers(), new ItemAmount(PLANKS, 5));
        manager.requestActions().onContainerChanged(hall);

        assertEquals(
                "building:0,64,0",
                colony.requests().resolverOf(planks).map(Resolver::resolverId).orElseThrow());
        assertEquals(
                "retrying",
                colony.requests().resolverOf(stone).map(Resolver::resolverId).orElseThrow());
    }
}
