package dev.hycolony.core.citizen.inventory;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.hycolony.core.kernel.item.ArmorInfo;
import dev.hycolony.core.kernel.item.ArmorInfo.Slot;
import org.junit.jupiter.api.Test;

/** MC ContainerCitizenInventory's armour slots: what a work building's level lets a citizen wear. */
class GuardGearTest {
    /** A helmet of MC armour level {@code level} (a reference ItemLevel of that level). */
    private static ArmorInfo helmet(int level) {
        int[] itemLevels = {15, 20, 25, 30, 40, 50};
        return new ArmorInfo(Slot.HEAD, itemLevels[level]);
    }

    @Test
    void levelOneAllowsLeatherToGold() {
        assertTrue(GuardGear.allows(1, helmet(0), Slot.HEAD));
        assertTrue(GuardGear.allows(1, helmet(1), Slot.HEAD));
        assertFalse(GuardGear.allows(1, helmet(2), Slot.HEAD));
    }

    @Test
    void levelsTwoAndThreeAllowLeatherToChainThenIron() {
        assertTrue(GuardGear.allows(2, helmet(2), Slot.HEAD));
        assertFalse(GuardGear.allows(2, helmet(3), Slot.HEAD));
        assertTrue(GuardGear.allows(3, helmet(0), Slot.HEAD));
        assertTrue(GuardGear.allows(3, helmet(3), Slot.HEAD));
        assertFalse(GuardGear.allows(3, helmet(4), Slot.HEAD));
    }

    @Test
    void levelFourAllowsChainToDiamondOnly() {
        assertFalse(GuardGear.allows(4, helmet(1), Slot.HEAD));
        assertTrue(GuardGear.allows(4, helmet(2), Slot.HEAD));
        assertTrue(GuardGear.allows(4, helmet(4), Slot.HEAD));
        assertFalse(GuardGear.allows(4, helmet(5), Slot.HEAD));
    }

    @Test
    void levelFiveAllowsIronAndAbove() {
        assertFalse(GuardGear.allows(5, helmet(2), Slot.HEAD));
        assertTrue(GuardGear.allows(5, helmet(3), Slot.HEAD));
        assertTrue(GuardGear.allows(5, helmet(5), Slot.HEAD));
        assertTrue(GuardGear.allows(9, helmet(5), Slot.HEAD), "beyond 5: as 5");
    }

    @Test
    void withoutWorkBuildingNothingIsAllowed() {
        assertFalse(GuardGear.allows(0, helmet(0), Slot.HEAD));
    }

    @Test
    void aPieceGoesInItsOwnSlotOnly() {
        assertFalse(GuardGear.allows(5, new ArmorInfo(Slot.CHEST, 30), Slot.HANDS));
        assertTrue(GuardGear.allows(5, new ArmorInfo(Slot.CHEST, 30), Slot.CHEST));
    }
}
