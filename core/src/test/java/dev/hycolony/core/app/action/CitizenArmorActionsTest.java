package dev.hycolony.core.app.action;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.hycolony.core.app.ColonyManager;
import dev.hycolony.core.building.Building;
import dev.hycolony.core.citizen.CitizenData;
import dev.hycolony.core.colony.Colony;
import dev.hycolony.core.kernel.BlockPos;
import dev.hycolony.core.kernel.item.ArmorInfo;
import dev.hycolony.core.kernel.item.ArmorInfo.Slot;
import dev.hycolony.core.kernel.item.Inventory;
import dev.hycolony.core.kernel.item.ItemAmount;
import dev.hycolony.core.kernel.item.ItemKey;
import dev.hycolony.core.kernel.port.BodyId;
import dev.hycolony.core.request.model.RequestState;
import dev.hycolony.core.request.model.RequestToken;
import dev.hycolony.core.request.model.StackRequest;
import dev.hycolony.core.testing.TestContexts;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/** MC ContainerCitizenInventory's armour slots: what may be worn, and what a piece put on does. */
class CitizenArmorActionsTest {
    private static final ItemKey LEATHER_CAP = new ItemKey("Armor_Leather_Light_Head");
    private static final ItemKey BRONZE_HELM = new ItemKey("Armor_Bronze_Head");
    private static final ItemKey BRONZE_CHEST = new ItemKey("Armor_Bronze_Chest");
    private static final ItemKey PLANKS = new ItemKey("Wood_Planks");
    private final TestContexts t = new TestContexts();
    private final ColonyManager manager = t.manager();
    private final UUID alice = UUID.randomUUID();
    private final BlockPos hall = new BlockPos(0, 64, 0);
    private final Colony colony;
    private final Building hut;
    private final CitizenData worker = new CitizenData(1);

    CitizenArmorActionsTest() {
        manager.foundation().begin(alice, "Alice", hall, 0);
        colony = manager.foundation().confirm(alice, "A").orElseThrow();
        hut = colony.buildings().at(hall).orElseThrow();
        hut.setLevel(1);
        worker.setWorkBuilding(hall);
        colony.citizens().restore(worker);
        t.catalog.armors.put(LEATHER_CAP, new ArmorInfo(Slot.HEAD, 15, 80));
        t.catalog.armors.put(BRONZE_HELM, new ArmorInfo(Slot.HEAD, 25, 100));
        t.catalog.armors.put(BRONZE_CHEST, new ArmorInfo(Slot.CHEST, 25, 100));
    }

    private boolean mayWear(int slot, ItemKey item) {
        return manager.citizenInventories().mayWear(colony.id(), worker.id(), slot, item);
    }

    /** The player puts {@code piece} in armour {@code slot}, as the window's armour container does. */
    private void playerWears(int slot, ItemAmount piece) {
        Inventory before = worker.equipment().armor().copy();
        worker.equipment().armor().set(slot, Optional.of(piece));
        manager.citizenInventories().onArmorEdit(colony.id(), worker.id(), before);
    }

    @Test
    void aWorkerAtALevelOneHutMayWearLeatherButNotBronze() {
        assertTrue(mayWear(Slot.HEAD.index(), LEATHER_CAP));
        assertFalse(mayWear(Slot.HEAD.index(), BRONZE_HELM), "MC level 1: leather to gold");

        hut.setLevel(2);

        assertTrue(mayWear(Slot.HEAD.index(), BRONZE_HELM), "MC level 2: leather to chain");
    }

    @Test
    void aCitizenWithoutWorkplaceMayWearNothing() {
        worker.setWorkBuilding(null);

        assertFalse(mayWear(Slot.HEAD.index(), LEATHER_CAP));
    }

    @Test
    void aPieceInTheWrongSlotOrAnythingElseIsRefused() {
        hut.setLevel(5);

        assertFalse(mayWear(Slot.HEAD.index(), BRONZE_CHEST));
        assertFalse(mayWear(Slot.HEAD.index(), PLANKS));
        assertFalse(mayWear(7, LEATHER_CAP), "no such armour slot");
        assertFalse(manager.citizenInventories().mayWear(colony.id(), 99, Slot.HEAD.index(), LEATHER_CAP));
    }

    @Test
    void aPiecePutOnClosesTheRequestForItAsMc() {
        RequestToken token =
                colony.requests().createAndAssign(hut, new StackRequest(LEATHER_CAP, 1, 1, true), worker.id());

        playerWears(Slot.HEAD.index(), new ItemAmount(LEATHER_CAP, 1));

        assertEquals(
                RequestState.COMPLETED,
                colony.requests().get(token).orElseThrow().state());
    }

    @Test
    void aPiecePutOnShowsOnTheBodyAndTheColonySaves() {
        assertTrue(colony.citizens().respawnBody(worker.id()));
        BodyId body = colony.citizens().bodyOf(worker.id()).orElseThrow();
        colony.clearDirty();

        playerWears(Slot.HEAD.index(), new ItemAmount(LEATHER_CAP, 1));

        assertEquals(
                List.of(
                        Optional.of(new ItemAmount(LEATHER_CAP, 1)),
                        Optional.empty(),
                        Optional.empty(),
                        Optional.empty()),
                t.bodies.bodies.get(body).armor);
        assertTrue(colony.isDirty());
    }
}
