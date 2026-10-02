package dev.hycolony.core.citizen.inventory;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.hycolony.core.app.ColonyManager;
import dev.hycolony.core.building.Building;
import dev.hycolony.core.citizen.CitizenData;
import dev.hycolony.core.citizen.inventory.CitizenEquipment.Hand;
import dev.hycolony.core.colony.Colony;
import dev.hycolony.core.construction.hut.ConstructionBuildingTypes;
import dev.hycolony.core.job.WorkerModule;
import dev.hycolony.core.kernel.BlockPos;
import dev.hycolony.core.kernel.item.ArmorInfo;
import dev.hycolony.core.kernel.item.ArmorInfo.Slot;
import dev.hycolony.core.kernel.item.ItemAmount;
import dev.hycolony.core.kernel.item.ItemKey;
import dev.hycolony.core.kernel.port.BodyId;
import dev.hycolony.core.testing.TestContexts;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/**
 * A citizen's armour and hands when it loses its job (MC AbstractJob.onRemoval) and when it is hurt (MC
 * CitizenItemUtils.damageArmor).
 */
class EquipmentLifeTest {
    private static final BlockPos HUT = new BlockPos(30, 64, 0);
    private static final ItemKey HELMET = new ItemKey("Armor_Iron_Head");
    private static final ItemKey CHEST = new ItemKey("Armor_Iron_Chest");
    private static final ItemKey PICK = new ItemKey("Tool_Pickaxe_Iron");
    private final TestContexts t = new TestContexts();
    private final UUID alice = UUID.randomUUID();
    private final ColonyManager manager = t.manager();
    private final Colony colony;
    private final Building hut;
    private final CitizenData bob = new CitizenData(1);

    EquipmentLifeTest() {
        manager.foundation().begin(alice, "Alice", new BlockPos(0, 64, 0), 0);
        colony = manager.foundation().confirm(alice, "A").orElseThrow();
        manager.huts().place(colony, ConstructionBuildingTypes.BUILDER.id(), HUT, 0, alice);
        hut = colony.buildings().at(HUT).orElseThrow();
        hut.setLevel(1);
        hut.setBuilt(true);
        colony.citizens().restore(bob);
        assertTrue(hut.module(WorkerModule.class).orElseThrow().hire(colony, hut, bob));
        t.catalog.armors.put(HELMET, new ArmorInfo(Slot.HEAD, 20, 100));
        t.catalog.armors.put(CHEST, new ArmorInfo(Slot.CHEST, 20, 100));
    }

    private void fire() {
        hut.module(WorkerModule.class).orElseThrow().fire(colony, hut, bob.id());
    }

    private BodyId body() {
        assertTrue(colony.citizens().respawnBody(bob.id()));
        return colony.citizens().bodyOf(bob.id()).orElseThrow();
    }

    @Test
    void aFiredWorkerPutsItsArmourBackInItsInventoryAndEmptiesItsHands() {
        BodyId body = body();
        bob.equipment().armor().set(Slot.HEAD.index(), Optional.of(new ItemAmount(HELMET, 1, 7)));
        bob.inventory().set(4, Optional.of(new ItemAmount(PICK, 1)));
        bob.equipment().hold(Hand.MAIN, 4);

        fire();

        assertEquals(Optional.empty(), bob.equipment().armor().slot(Slot.HEAD.index()));
        assertTrue(bob.inventory().contents().contains(new ItemAmount(HELMET, 1, 7)), "with its wear");
        assertEquals(CitizenEquipment.NO_SLOT, bob.equipment().held(Hand.MAIN));
        assertEquals(
                List.of(Optional.empty(), Optional.empty(), Optional.empty(), Optional.empty()),
                t.bodies.bodies.get(body).armor);
    }

    @Test
    void armourThatDoesNotFitInTheInventoryStaysWorn() {
        ItemKey dirt = new ItemKey("Soil_Dirt");
        for (int i = 0; i < CitizenData.INVENTORY_SLOTS; i++) {
            bob.inventory().set(i, Optional.of(new ItemAmount(dirt, t.catalog.defaultMaxStack)));
        }
        bob.equipment().armor().set(Slot.HEAD.index(), Optional.of(new ItemAmount(HELMET, 1)));

        fire();

        assertEquals(
                Optional.of(new ItemAmount(HELMET, 1)), bob.equipment().armor().slot(Slot.HEAD.index()));
    }

    @Test
    void eachPieceLosesAQuarterOfTheDamageAtLeastOnePoint() {
        bob.equipment().armor().set(Slot.HEAD.index(), Optional.of(new ItemAmount(HELMET, 1)));
        bob.equipment().armor().set(Slot.CHEST.index(), Optional.of(new ItemAmount(CHEST, 1, 10)));

        ArmorWear.onHurt(colony, bob, 40); // 40 % of its health: 8 of MC's 20 points, 2 per piece
        ArmorWear.onHurt(colony, bob, 5); // 1 MC point: at least 1 per piece

        assertEquals(
                3, bob.equipment().armor().slot(Slot.HEAD.index()).orElseThrow().damage());
        assertEquals(
                13,
                bob.equipment().armor().slot(Slot.CHEST.index()).orElseThrow().damage());
    }

    @Test
    void aPieceWornOutIsGoneFromTheCitizenAndItsBody() {
        BodyId body = body();
        bob.equipment().armor().set(Slot.HEAD.index(), Optional.of(new ItemAmount(HELMET, 1, 99)));

        ArmorWear.onHurt(colony, bob, 40);

        assertEquals(Optional.empty(), bob.equipment().armor().slot(Slot.HEAD.index()));
        assertEquals(Optional.empty(), t.bodies.bodies.get(body).armor.get(Slot.HEAD.index()));
    }

    @Test
    void anUnknownPieceOrNoArmourWearsNothing() {
        ItemKey odd = new ItemKey("Unknown_Hat");
        bob.equipment().armor().set(Slot.HEAD.index(), Optional.of(new ItemAmount(odd, 1)));

        ArmorWear.onHurt(colony, bob, 100);

        assertEquals(
                Optional.of(new ItemAmount(odd, 1)), bob.equipment().armor().slot(Slot.HEAD.index()));
    }
}
