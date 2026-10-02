package dev.hycolony.core.kernel.item;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

/** An armour piece as the catalog tells it (spec 2026-10-02 citizen inventory, § 3). */
class ArmorInfoTest {
    @Test
    void slotsFollowHytalesArmorSlotOrder() {
        assertEquals(0, ArmorInfo.Slot.HEAD.index());
        assertEquals(1, ArmorInfo.Slot.CHEST.index());
        assertEquals(2, ArmorInfo.Slot.HANDS.index());
        assertEquals(3, ArmorInfo.Slot.LEGS.index());
    }

    @Test
    void aNegativeItemLevelOrDurabilityIsRefused() {
        assertThrows(IllegalArgumentException.class, () -> new ArmorInfo(ArmorInfo.Slot.HEAD, -1, 100));
        assertThrows(IllegalArgumentException.class, () -> new ArmorInfo(ArmorInfo.Slot.HEAD, 20, -1));
    }
}
