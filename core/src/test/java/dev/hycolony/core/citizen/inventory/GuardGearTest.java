package dev.hycolony.core.citizen.inventory;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.hycolony.core.kernel.item.ArmorInfo;
import dev.hycolony.core.kernel.item.ArmorInfo.Slot;
import org.junit.jupiter.api.Test;

/**
 * MC ContainerCitizenInventory's armour slots: what a work building's level lets a citizen wear
 * (EquipmentLevelConstants ranges, against MC armour levels leather 1, chain 2, iron 3, diamond 4, beyond 5).
 */
class GuardGearTest {
    /** A helmet of MC armour level {@code level} 1 to 5 (the reference ItemLevel of that level). */
    private static ArmorInfo helmet(int level) {
        int[] itemLevels = {0, 15, 25, 30, 40, 50};
        return new ArmorInfo(Slot.HEAD, itemLevels[level], 100);
    }

    @Test
    void levelOneAllowsLeatherAndGoldOnly() {
        assertTrue(GuardGear.allows(1, helmet(1), Slot.HEAD));
        assertFalse(GuardGear.allows(1, helmet(2), Slot.HEAD), "chain");
    }

    @Test
    void levelsTwoAndThreeAllowUpToChainThenIron() {
        assertTrue(GuardGear.allows(2, helmet(2), Slot.HEAD));
        assertFalse(GuardGear.allows(2, helmet(3), Slot.HEAD));
        assertTrue(GuardGear.allows(3, helmet(1), Slot.HEAD));
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
    }

    @Test
    void withoutWorkBuildingOrBeyondLevelFiveNothingIsAllowed() {
        assertFalse(GuardGear.allows(0, helmet(1), Slot.HEAD));
        assertFalse(GuardGear.allows(6, helmet(5), Slot.HEAD), "MC: default -> empty list");
    }

    @Test
    void aPieceGoesInItsOwnSlotOnly() {
        assertFalse(GuardGear.allows(5, new ArmorInfo(Slot.CHEST, 30, 100), Slot.HANDS));
        assertTrue(GuardGear.allows(5, new ArmorInfo(Slot.CHEST, 30, 100), Slot.CHEST));
    }
}
