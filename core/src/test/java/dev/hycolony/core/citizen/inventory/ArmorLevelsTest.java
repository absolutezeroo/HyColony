package dev.hycolony.core.citizen.inventory;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

/** MC ItemStackUtils.getArmorLevel, on Hytale's ItemLevel (spec 2026-10-02 citizen inventory, § 3 table). */
class ArmorLevelsTest {
    @Test
    void eachReferencePieceIsTheTopOfItsLevel() {
        assertEquals(0, ArmorLevels.of(10), "copper, wool");
        assertEquals(0, ArmorLevels.of(15), "light leather, MC leather");
        assertEquals(1, ArmorLevels.of(16));
        assertEquals(1, ArmorLevels.of(20), "iron, MC gold");
        assertEquals(2, ArmorLevels.of(25), "bronze, MC chain");
        assertEquals(3, ArmorLevels.of(30), "thorium, MC iron");
        assertEquals(4, ArmorLevels.of(35), "cobalt");
        assertEquals(4, ArmorLevels.of(40), "adamantite, MC diamond");
        assertEquals(5, ArmorLevels.of(41));
        assertEquals(5, ArmorLevels.of(75), "prisma");
    }
}
