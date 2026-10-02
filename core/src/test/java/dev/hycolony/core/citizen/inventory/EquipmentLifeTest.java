package dev.hycolony.core.citizen.inventory;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import dev.hycolony.core.app.ColonyManager;
import dev.hycolony.core.app.persistence.ColonySerializer;
import dev.hycolony.core.building.Building;
import dev.hycolony.core.citizen.CitizenData;
import dev.hycolony.core.citizen.inventory.CitizenEquipment.Hand;
import dev.hycolony.core.colony.Colony;
import dev.hycolony.core.colony.territory.TerritoryIndex;
import dev.hycolony.core.construction.hut.ConstructionBuildingTypes;
import dev.hycolony.core.job.WorkerModule;
import dev.hycolony.core.kernel.BlockPos;
import dev.hycolony.core.kernel.item.ArmorInfo;
import dev.hycolony.core.kernel.item.ArmorInfo.Slot;
import dev.hycolony.core.kernel.item.ItemAmount;
import dev.hycolony.core.kernel.item.ItemKey;
import dev.hycolony.core.kernel.port.BodyId;
import dev.hycolony.core.testing.TestContexts;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/**
 * A citizen's armour and hands when it loses its job (MC AbstractJob.onRemoval) and when it is hurt (Hytale's armour
 * wear, DamageSystems.DamageArmor, for MC CitizenItemUtils.updateArmorDamage).
 */
class EquipmentLifeTest {
    private static final BlockPos HUT = new BlockPos(30, 64, 0);
    private static final ItemKey HELMET = new ItemKey("Armor_Iron_Head");
    private static final ItemKey CHEST = new ItemKey("Armor_Iron_Chest");
    private static final ItemKey PICK = new ItemKey("Tool_Pickaxe_Iron");
    /** Iron armour's hits before it breaks: MaxDurability 100 / DurabilityLossOnHit 0.5. */
    private static final int HITS = 200;

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
        t.catalog.durability.put(HELMET, HITS);
        t.catalog.durability.put(CHEST, HITS);
    }

    private void fire() {
        hut.module(WorkerModule.class).orElseThrow().fire(colony, hut, bob.id());
    }

    private BodyId body() {
        assertTrue(colony.citizens().respawnBody(bob.id()));
        return colony.citizens().bodyOf(bob.id()).orElseThrow();
    }

    private void wear(Slot slot, ItemKey item, int damage) {
        bob.equipment().armor().set(slot.index(), Optional.of(new ItemAmount(item, 1, damage)));
    }

    private int damage(Slot slot) {
        return bob.equipment().armor().slot(slot.index()).orElseThrow().damage();
    }

    @Test
    void aFiredWorkerPutsItsArmourBackInItsInventoryAndItsBodysHandsGoEmpty() {
        BodyId body = body();
        wear(Slot.HEAD, HELMET, 7);
        bob.inventory().set(4, Optional.of(new ItemAmount(PICK, 1)));
        HeldItems.holdSlot(bob, t.bodies, body, 4);
        HeldItems.showArmor(bob, t.bodies, body);
        assertEquals(PICK, t.bodies.bodies.get(body).held);
        assertTrue(t.bodies.bodies.get(body).armor.get(Slot.HEAD.index()).isPresent());

        fire();

        assertEquals(Optional.empty(), bob.equipment().armor().slot(Slot.HEAD.index()));
        assertTrue(bob.inventory().contents().contains(new ItemAmount(HELMET, 1, 7)), "with its wear");
        assertEquals(4, bob.equipment().held(Hand.MAIN), "MC's moveArmorToInventory leaves the held slots");
        assertNull(t.bodies.bodies.get(body).held, "MC empties the entity's hands");
        assertTrue(t.bodies.bodies.get(body).armor.stream().allMatch(Optional::isEmpty));
    }

    @Test
    void aNewBodyShowsTheArmourButLeavesItsHandToTheAi() {
        wear(Slot.HEAD, HELMET, 7);
        bob.inventory().set(4, Optional.of(new ItemAmount(PICK, 1)));
        bob.equipment().hold(Hand.MAIN, 4);

        BodyId body = body();

        assertNull(t.bodies.bodies.get(body).held, "MC's entity hand is its own, not the InventoryCitizen's");
        assertEquals(4, bob.equipment().held(Hand.MAIN));
        assertEquals(
                Optional.of(new ItemAmount(HELMET, 1, 7)),
                t.bodies.bodies.get(body).armor.get(Slot.HEAD.index()));
    }

    @Test
    void aWorkerWhoseHutIsGoneFromTheSaveTakesItsArmourOff() {
        wear(Slot.HEAD, HELMET, 7);
        JsonObject saved = ColonySerializer.write(colony);
        saved.add("buildings", new JsonArray());

        Colony loaded = ColonySerializer.read(saved, t.context(), new TerritoryIndex());

        CitizenData again = loaded.citizens().get(bob.id()).orElseThrow();
        assertTrue(again.job().isEmpty(), "freed by the load's heal");
        assertEquals(Optional.empty(), again.equipment().armor().slot(Slot.HEAD.index()));
        assertTrue(again.inventory().contents().contains(new ItemAmount(HELMET, 1, 7)), "MC AbstractJob.onRemoval");
    }

    @Test
    void armourThatDoesNotFitInTheInventoryStaysWorn() {
        ItemKey dirt = new ItemKey("Soil_Dirt");
        for (int i = 0; i < CitizenData.INVENTORY_SLOTS; i++) {
            bob.inventory().set(i, Optional.of(new ItemAmount(dirt, t.catalog.defaultMaxStack)));
        }
        wear(Slot.HEAD, HELMET, 0);

        fire();

        assertEquals(
                Optional.of(new ItemAmount(HELMET, 1)), bob.equipment().armor().slot(Slot.HEAD.index()));
    }

    @Test
    void aHitWearsOnePieceByOneHit() {
        wear(Slot.HEAD, HELMET, 0);
        wear(Slot.CHEST, CHEST, 10);

        ArmorWear.onHurt(colony, bob);

        assertEquals(11, damage(Slot.HEAD) + damage(Slot.CHEST), "one piece, one hit (Hytale's DamageArmor)");
    }

    @Test
    void aBrokenPieceStaysWornAndTheOthersTakeTheHits() {
        BodyId body = body();
        wear(Slot.HEAD, HELMET, HITS - 1);

        ArmorWear.onHurt(colony, bob);
        wear(Slot.CHEST, CHEST, 0);
        ArmorWear.onHurt(colony, bob);
        ArmorWear.onHurt(colony, bob);

        assertEquals(HITS, damage(Slot.HEAD), "broken, still worn: Hytale keeps a broken piece");
        assertEquals(2, damage(Slot.CHEST), "only an unbroken piece wears");
        assertEquals(
                Optional.of(new ItemAmount(HELMET, 1, HITS)),
                t.bodies.bodies.get(body).armor.get(Slot.HEAD.index()),
                "the body wears it broken, as it protects less");
    }

    @Test
    void anUnbreakablePieceOrNoArmourWearsNothing() {
        ItemKey hat = new ItemKey("Unbreakable_Hat");
        wear(Slot.HEAD, hat, 0);
        colony.clearDirty();

        ArmorWear.onHurt(colony, bob);

        assertEquals(0, damage(Slot.HEAD));
        assertFalse(colony.isDirty(), "nothing changed");
    }

    @Test
    void anUnbreakablePieceTakesItsShareOfTheHits() {
        wear(Slot.HEAD, new ItemKey("Unbreakable_Hat"), 0);
        wear(Slot.CHEST, CHEST, 0);

        for (int i = 0; i < HITS; i++) {
            ArmorWear.onHurt(colony, bob);
        }

        assertTrue(damage(Slot.CHEST) < HITS * 3 / 4, "DamageArmor draws among every unbroken piece: about half");
        assertTrue(damage(Slot.CHEST) > HITS / 4, damage(Slot.CHEST) + " hits on the chest");
    }
}
