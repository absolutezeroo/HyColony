package dev.hycolony.core.citizen.inventory;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import dev.hycolony.core.citizen.inventory.CitizenEquipment.Hand;
import org.junit.jupiter.api.Test;

/** MC InventoryCitizen's armour slots and held slots (spec 2026-10-02 citizen inventory, § 3). */
class CitizenEquipmentTest {
    private final CitizenEquipment equipment = new CitizenEquipment();

    @Test
    void handsStartEmptyAndHoldASlot() {
        assertEquals(CitizenEquipment.NO_SLOT, equipment.held(Hand.MAIN));
        assertEquals(CitizenEquipment.NO_SLOT, equipment.held(Hand.OFF));

        equipment.hold(Hand.MAIN, 3);
        equipment.hold(Hand.OFF, 0);

        assertEquals(3, equipment.held(Hand.MAIN));
        assertEquals(0, equipment.held(Hand.OFF));
    }

    @Test
    void aSlotOutsideTheInventoryIsRefused() {
        assertThrows(IllegalArgumentException.class, () -> equipment.hold(Hand.MAIN, 27));
        assertThrows(IllegalArgumentException.class, () -> equipment.hold(Hand.OFF, -2));
    }

    @Test
    void armorHasFourSlots() {
        assertEquals(4, equipment.armor().size());
    }
}
