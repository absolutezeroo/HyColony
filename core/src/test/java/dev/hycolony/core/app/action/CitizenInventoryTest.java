package dev.hycolony.core.app.action;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.hycolony.core.app.ColonyManager;
import dev.hycolony.core.building.Building;
import dev.hycolony.core.citizen.CitizenData;
import dev.hycolony.core.colony.Colony;
import dev.hycolony.core.kernel.BlockPos;
import dev.hycolony.core.kernel.item.Inventory;
import dev.hycolony.core.kernel.item.ItemAmount;
import dev.hycolony.core.kernel.item.ItemKey;
import dev.hycolony.core.kernel.item.ToolInfo;
import dev.hycolony.core.kernel.item.ToolType;
import dev.hycolony.core.kernel.port.Msg;
import dev.hycolony.core.request.Request;
import dev.hycolony.core.request.model.RequestState;
import dev.hycolony.core.request.model.RequestToken;
import dev.hycolony.core.request.model.StackRequest;
import dev.hycolony.core.request.model.ToolRequest;
import dev.hycolony.core.testing.FakeNotifier;
import dev.hycolony.core.testing.FakeUi;
import dev.hycolony.core.testing.TestContexts;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class CitizenInventoryTest {
    private static final ItemKey PLANKS = new ItemKey("Wood_Planks");
    private static final ItemKey DIRT = new ItemKey("Dirt");
    private static final ItemKey SHOVEL = new ItemKey("Tool_Shovel_Crude");
    private final TestContexts t = new TestContexts();
    private final ColonyManager manager = t.manager();
    private final UUID alice = UUID.randomUUID();
    private final UUID bob = UUID.randomUUID();
    private final BlockPos hall = new BlockPos(0, 64, 0);
    private final Colony colony;
    private final Building hut;
    private final CitizenData worker = new CitizenData(1);
    private final CitizenData other = new CitizenData(2);

    CitizenInventoryTest() {
        manager.foundation().begin(alice, "Alice", hall, 0);
        colony = manager.foundation().confirm(alice, "A").orElseThrow();
        hut = colony.buildings().at(hall).orElseThrow();
        worker.setWorkBuilding(hall);
        other.setWorkBuilding(hall);
        colony.citizens().restore(worker);
        colony.citizens().restore(other);
    }

    private RequestToken request(int count, int citizenId) {
        return colony.requests().createAndAssign(hut, new StackRequest(PLANKS, count, count, true), citizenId);
    }

    private Request get(RequestToken token) {
        return colony.requests().get(token).orElseThrow();
    }

    /** The player sets slot {@code slot} of the worker's inventory to {@code stack}, as the Hytale window does. */
    private void playerSets(int slot, Optional<ItemAmount> stack) {
        Inventory before = worker.inventory().copy();
        worker.inventory().set(slot, stack);
        manager.citizenInventories().onPlayerEdit(colony.id(), worker.id(), before);
    }

    @Test
    void openingNeedsManageHutsThenAsksTheUiToOpenTheContainer() {
        manager.citizenInventories().open(alice, colony.id(), worker.id());

        assertEquals(List.of(new FakeUi.OpenedInventory(alice, colony.id(), worker.id())), t.ui.openedInventories);
    }

    @Test
    void openingWithoutManageHutsIsRefusedWithAMessage() {
        t.notifier.sent.clear();

        manager.citizenInventories().open(bob, colony.id(), worker.id());

        assertTrue(t.ui.openedInventories.isEmpty());
        assertEquals(
                List.of(new FakeNotifier.Sent(bob, Msg.of("hycolony.permission.denied", colony.name()))),
                t.notifier.sent);
    }

    @Test
    void openingAnUnknownCitizenDoesNothing() {
        manager.citizenInventories().open(alice, colony.id(), 99);
        manager.citizenInventories().open(alice, 42, worker.id());

        assertTrue(t.ui.openedInventories.isEmpty());
    }

    @Test
    void puttingAStackOverrulesTheCitizensFirstMatchingOpenRequest() {
        RequestToken first = request(10, worker.id());
        RequestToken second = request(3, worker.id());

        playerSets(0, Optional.of(new ItemAmount(PLANKS, 4)));

        assertEquals(RequestState.COMPLETED, get(first).state());
        assertEquals(List.of(new ItemAmount(PLANKS, 4)), get(first).deliveries());
        assertTrue(get(first).deliveredToCitizen(), "the items are already in the citizen's inventory");
        assertEquals(RequestState.IN_PROGRESS, get(second).state());
    }

    @Test
    void toppingUpAStackOverrulesNothingLikeMcSetChanged() {
        worker.inventory().set(0, Optional.of(new ItemAmount(PLANKS, 2)));
        RequestToken token = request(10, worker.id());

        playerSets(0, Optional.of(new ItemAmount(PLANKS, 5)));

        assertEquals(RequestState.IN_PROGRESS, get(token).state());
    }

    @Test
    void replacingAStackWithAnotherItemOverrulesWithTheNewStack() {
        worker.inventory().set(0, Optional.of(new ItemAmount(DIRT, 2)));
        RequestToken token = request(10, worker.id());

        playerSets(0, Optional.of(new ItemAmount(PLANKS, 5)));

        assertEquals(List.of(new ItemAmount(PLANKS, 5)), get(token).deliveries());
    }

    @Test
    void twoSlotsFilledInOneMoveResolveTwoRequests() {
        RequestToken first = request(10, worker.id());
        RequestToken second = request(3, worker.id());
        Inventory before = worker.inventory().copy();
        worker.inventory().set(0, Optional.of(new ItemAmount(PLANKS, 4)));
        worker.inventory().set(1, Optional.of(new ItemAmount(PLANKS, 2)));

        manager.citizenInventories().onPlayerEdit(colony.id(), worker.id(), before);

        assertEquals(List.of(new ItemAmount(PLANKS, 4)), get(first).deliveries());
        assertEquals(List.of(new ItemAmount(PLANKS, 2)), get(second).deliveries());
    }

    @Test
    void anEditForAnUnknownColonyOrCitizenDoesNothing() {
        RequestToken token = request(10, worker.id());
        Inventory before = new Inventory(CitizenData.INVENTORY_SLOTS);
        colony.clearDirty();

        manager.citizenInventories().onPlayerEdit(42, worker.id(), before);
        manager.citizenInventories().onPlayerEdit(colony.id(), 99, before);

        assertEquals(RequestState.IN_PROGRESS, get(token).state());
        assertFalse(colony.isDirty());
    }

    @Test
    void takingItemsOverrulesNothing() {
        worker.inventory().set(0, Optional.of(new ItemAmount(PLANKS, 8)));
        RequestToken token = request(10, worker.id());

        playerSets(0, Optional.of(new ItemAmount(PLANKS, 3)));
        playerSets(0, Optional.empty());

        assertEquals(RequestState.IN_PROGRESS, get(token).state());
    }

    @Test
    void anotherCitizensRequestOrAnotherItemIsNotOverruled() {
        RequestToken othersRequest = request(10, other.id());
        RequestToken hutRequest = request(10, -1);

        playerSets(0, Optional.of(new ItemAmount(PLANKS, 4)));
        playerSets(1, Optional.of(new ItemAmount(DIRT, 4)));

        assertEquals(RequestState.IN_PROGRESS, get(othersRequest).state());
        assertEquals(RequestState.IN_PROGRESS, get(hutRequest).state());
    }

    @Test
    void aCitizenWithoutWorkplaceOverrulesNothing() {
        RequestToken token = request(10, worker.id());
        worker.setWorkBuilding(null);

        playerSets(0, Optional.of(new ItemAmount(PLANKS, 4)));

        assertEquals(RequestState.IN_PROGRESS, get(token).state());
    }

    @Test
    void anyPlayerEditMarksTheColonyToSave() {
        worker.setWorkBuilding(null);
        colony.clearDirty();

        playerSets(0, Optional.empty());

        assertTrue(colony.isDirty());
    }

    /** MC: a broken tool no longer exists, so putting one in the citizen's inventory closes no tool request. */
    @Test
    void puttingABrokenToolOverrulesNoToolRequest() {
        t.catalog.tools.put(SHOVEL, new ToolInfo(ToolType.SHOVEL, 0, 1f));
        t.catalog.durability.put(SHOVEL, 150);
        RequestToken token =
                colony.requests().createAndAssign(hut, new ToolRequest(ToolType.SHOVEL, 0, 5), worker.id());

        playerSets(0, Optional.of(new ItemAmount(SHOVEL, 1, 150)));

        assertEquals(RequestState.IN_PROGRESS, get(token).state());
        playerSets(1, Optional.of(new ItemAmount(SHOVEL, 1, 20)));
        assertEquals(RequestState.COMPLETED, get(token).state());
    }
}
