package dev.hycolony.core.citizen.inventory;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import dev.hycolony.core.citizen.CitizenData;
import dev.hycolony.core.citizen.inventory.CitizenEquipment.Hand;
import dev.hycolony.core.kernel.BlockPos;
import dev.hycolony.core.kernel.WorldKey;
import dev.hycolony.core.kernel.item.ItemAmount;
import dev.hycolony.core.kernel.item.ItemKey;
import dev.hycolony.core.kernel.port.BodyId;
import dev.hycolony.core.testing.FakeBodies;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

/** MC CitizenItemUtils.setHeldItem(hand, slot) and the body showing its armour (spec 2026-10-02, § 3). */
class HeldItemsTest {
    private static final ItemKey PICK = new ItemKey("Tool_Pickaxe_Iron");
    private static final ItemKey SEEDS = new ItemKey("Plant_Seeds_Wheat");
    private static final ItemKey HELMET = new ItemKey("Armor_Iron_Head");
    private final FakeBodies bodies = new FakeBodies();
    private final BodyId body = bodies.spawn(new WorldKey("default"), new BlockPos(0, 64, 0), 1, 1, "Bob")
            .orElseThrow();
    private final CitizenData d = new CitizenData(1);

    private FakeBodies.Body shown() {
        return bodies.bodies.get(body);
    }

    @Test
    void holdingASlotPointsTheHandAtItAndShowsItsItem() {
        d.inventory().set(3, Optional.of(new ItemAmount(PICK, 1)));

        HeldItems.holdSlot(d, bodies, body, 3);

        assertEquals(3, d.equipment().held(Hand.MAIN));
        assertEquals(PICK, shown().held);
    }

    @Test
    void holdingAnItemOfTheInventoryPointsAtItsFirstSlot() {
        d.inventory().set(5, Optional.of(new ItemAmount(SEEDS, 8)));
        d.inventory().set(7, Optional.of(new ItemAmount(SEEDS, 2)));

        HeldItems.holdItem(d, bodies, body, Optional.of(SEEDS));

        assertEquals(5, d.equipment().held(Hand.MAIN));
        assertEquals(SEEDS, shown().held);
    }

    @Test
    void anItemNotInTheInventoryIsShownWithoutASlot() {
        HeldItems.holdItem(d, bodies, body, Optional.of(PICK));

        assertEquals(CitizenEquipment.NO_SLOT, d.equipment().held(Hand.MAIN));
        assertEquals(PICK, shown().held);
    }

    @Test
    void clearEmptiesTheHandAndItsSlot() {
        d.inventory().set(3, Optional.of(new ItemAmount(PICK, 1)));
        HeldItems.holdSlot(d, bodies, body, 3);

        HeldItems.clear(d, bodies, body);

        assertEquals(CitizenEquipment.NO_SLOT, d.equipment().held(Hand.MAIN));
        assertNull(shown().held);
    }

    @Test
    void aNewBodyShowsTheHeldSlotAndTheArmour() {
        d.inventory().set(2, Optional.of(new ItemAmount(PICK, 1)));
        d.equipment().hold(Hand.MAIN, 2);
        d.equipment().armor().set(0, Optional.of(new ItemAmount(HELMET, 1)));

        HeldItems.show(d, bodies, body);

        assertEquals(PICK, shown().held);
        assertEquals(List.of(Optional.of(HELMET), Optional.empty(), Optional.empty(), Optional.empty()), shown().armor);
    }

    @Test
    void aHeldSlotSinceEmptiedShowsAnEmptyHand() {
        d.equipment().hold(Hand.MAIN, 4);

        HeldItems.show(d, bodies, body);

        assertNull(shown().held);
        assertEquals(4, d.equipment().held(Hand.MAIN), "MC keeps the slot");
    }
}
