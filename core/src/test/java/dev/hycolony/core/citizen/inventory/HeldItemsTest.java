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

/**
 * MC CitizenItemUtils.setHeldItem(hand, slot), the dump releasing a stored held slot, and the body showing its armour
 * (spec 2026-10-02, § 3).
 */
class HeldItemsTest {
    private static final ItemKey PICK = new ItemKey("Tool_Pickaxe_Iron");
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
    void storingTheHeldSlotReleasesTheHandAndEmptiesTheBodys() {
        d.inventory().set(3, Optional.of(new ItemAmount(PICK, 1)));
        HeldItems.holdSlot(d, bodies, body, 3);
        d.equipment().hold(Hand.OFF, 3);

        HeldItems.release(d, bodies, Optional.of(body), 3);

        assertEquals(CitizenEquipment.NO_SLOT, d.equipment().held(Hand.MAIN));
        assertEquals(CitizenEquipment.NO_SLOT, d.equipment().held(Hand.OFF));
        assertNull(shown().held);
    }

    @Test
    void storingAnotherSlotLeavesTheHandAlone() {
        d.inventory().set(3, Optional.of(new ItemAmount(PICK, 1)));
        HeldItems.holdSlot(d, bodies, body, 3);

        HeldItems.release(d, bodies, Optional.of(body), 5);

        assertEquals(3, d.equipment().held(Hand.MAIN));
        assertEquals(PICK, shown().held);
    }

    @Test
    void theBodyWearsTheArmourWithItsWear() {
        d.equipment().armor().set(0, Optional.of(new ItemAmount(HELMET, 1, 7)));

        HeldItems.showArmor(d, bodies, body);

        assertEquals(
                List.of(
                        Optional.of(new ItemAmount(HELMET, 1, 7)),
                        Optional.empty(),
                        Optional.empty(),
                        Optional.empty()),
                shown().armor);
    }
}
