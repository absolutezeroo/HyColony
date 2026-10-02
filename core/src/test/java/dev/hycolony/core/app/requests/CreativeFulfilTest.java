package dev.hycolony.core.app.requests;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.hycolony.core.app.ColonyManager;
import dev.hycolony.core.building.Building;
import dev.hycolony.core.citizen.CitizenData;
import dev.hycolony.core.colony.Colony;
import dev.hycolony.core.kernel.BlockPos;
import dev.hycolony.core.kernel.item.ItemAmount;
import dev.hycolony.core.kernel.item.ItemKey;
import dev.hycolony.core.kernel.item.ToolInfo;
import dev.hycolony.core.kernel.item.ToolType;
import dev.hycolony.core.request.Request;
import dev.hycolony.core.request.model.RequestState;
import dev.hycolony.core.request.model.RequestToken;
import dev.hycolony.core.request.model.StackList;
import dev.hycolony.core.request.model.StackRequest;
import dev.hycolony.core.request.model.ToolRequest;
import dev.hycolony.core.testing.TestContexts;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/**
 * MC's request window "Fulfill" in creative mode (RequestWindowCitizen.onFulfill and
 * TransferItemsToCitizenRequestMessage): the request's first displayed item, as many as asked, for free; the request
 * closed with that amount.
 */
class CreativeFulfilTest {
    private static final ItemKey PLANKS = new ItemKey("Wood_Planks");
    private final TestContexts t = new TestContexts();
    private final ColonyManager manager = t.manager();
    private final UUID alice = UUID.randomUUID();
    private final BlockPos hall = new BlockPos(0, 64, 0);
    private final Colony colony;
    private final Building hut;
    private final CitizenData citizen = new CitizenData(1);

    CreativeFulfilTest() {
        manager.foundation().begin(alice, "Alice", hall, 0);
        colony = manager.foundation().confirm(alice, "A").orElseThrow();
        hut = colony.buildings().at(hall).orElseThrow();
        colony.citizens().restore(citizen);
        t.players.creative.add(alice);
    }

    private Request get(RequestToken token) {
        return colony.requests().get(token).orElseThrow();
    }

    @Test
    void creativeGivesTheRequestedItemsForFreeAndCloses() {
        RequestToken token =
                colony.requests().createAndAssign(hut, new StackRequest(PLANKS, 10, 10, true), citizen.id());

        assertTrue(manager.requestActions().fulfil(alice, colony.id(), token));

        assertEquals(10, citizen.inventory().count(PLANKS));
        assertEquals(0, t.playerInventory.count(alice, PLANKS), "the player had none and gave none");
        assertEquals(RequestState.COMPLETED, get(token).state());
        assertEquals(List.of(new ItemAmount(PLANKS, 10)), get(token).deliveries());
    }

    @Test
    void creativeKeepsWhatThePlayerHolds() {
        RequestToken token =
                colony.requests().createAndAssign(hut, new StackRequest(PLANKS, 10, 10, true), citizen.id());
        t.playerInventory.give(alice, new ItemAmount(PLANKS, 4));

        manager.requestActions().fulfil(alice, colony.id(), token);

        assertEquals(4, t.playerInventory.count(alice, PLANKS));
        assertEquals(10, citizen.inventory().count(PLANKS));
    }

    @Test
    void creativeGivesAListsFirstItem() {
        ItemKey stone = new ItemKey("Rock_Stone");
        RequestToken token = colony.requests()
                .createAndAssign(hut, new StackList(List.of(stone, PLANKS), "any", 3, 3), citizen.id());

        assertTrue(manager.requestActions().fulfil(alice, colony.id(), token));

        assertEquals(3, citizen.inventory().count(stone));
    }

    @Test
    void creativeGivesTheFirstCatalogToolTheRequestAccepts() {
        ItemKey stonePick = new ItemKey("Pick_Stone");
        t.catalog.tools.put(new ItemKey("Pick_Iron"), new ToolInfo(ToolType.PICKAXE, 2, 1f));
        t.catalog.tools.put(stonePick, new ToolInfo(ToolType.PICKAXE, 1, 1f));
        t.catalog.tools.put(new ItemKey("Pick_Wood"), new ToolInfo(ToolType.PICKAXE, 0, 1f));
        RequestToken token =
                colony.requests().createAndAssign(hut, new ToolRequest(ToolType.PICKAXE, 1, 3), citizen.id());

        assertTrue(manager.requestActions().fulfil(alice, colony.id(), token));

        assertEquals(1, citizen.inventory().count(new ItemKey("Pick_Iron")), "Pick_Iron sorts before Pick_Stone");
        assertEquals(
                List.of(new ItemAmount(new ItemKey("Pick_Iron"), 1)), get(token).deliveries());
    }

    @Test
    void creativeWithNoToolTheRequestAcceptsClosesNothing() {
        RequestToken token =
                colony.requests().createAndAssign(hut, new ToolRequest(ToolType.SHOVEL, 0, 3), citizen.id());

        assertFalse(manager.requestActions().fulfil(alice, colony.id(), token));

        assertTrue(get(token).state().isBefore(RequestState.COMPLETED));
    }

    @Test
    void creativeClosesWithTheAmountAskedEvenIntoAFullCitizenAsMc() {
        ItemKey dirt = new ItemKey("Dirt");
        t.catalog.maxStacks.put(PLANKS, 5);
        for (int i = 0; i < CitizenData.INVENTORY_SLOTS - 1; i++) {
            citizen.inventory().insert(new ItemAmount(dirt, 64), k -> 64);
        }
        RequestToken token =
                colony.requests().createAndAssign(hut, new StackRequest(PLANKS, 10, 10, true), citizen.id());

        assertTrue(manager.requestActions().fulfil(alice, colony.id(), token));

        assertEquals(5, citizen.inventory().count(PLANKS), "what fits");
        assertEquals(0, t.playerInventory.count(alice, PLANKS), "nothing comes back: it was free");
        assertEquals(
                List.of(new ItemAmount(PLANKS, 10)), get(token).deliveries(), "MC overrules with the amount asked");
    }
}
