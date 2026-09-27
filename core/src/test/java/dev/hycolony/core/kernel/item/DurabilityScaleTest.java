package dev.hycolony.core.kernel.item;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class DurabilityScaleTest {
    /** Tool_Pickaxe_Iron: 250 Hytale points for 500 blocks, so one use is 0.5 point. */
    private static final double IRON_MAX = 250;

    private static final int IRON_USES = 500;

    @Test
    void everyDamageReadsBackAsTheSameDamage() {
        for (int damage = 0; damage < IRON_USES; damage++) {
            double durability = DurabilityScale.durability(damage, IRON_USES, IRON_MAX);
            assertEquals(damage, DurabilityScale.damage(durability, IRON_USES, IRON_MAX));
        }
        assertEquals(149.0, DurabilityScale.durability(1, 150, 150));
    }

    @Test
    void aPartlyUsedStepCountsAsAWholeUseSoTheConversionNeverRepairs() {
        // A player's hit costs 0.25 point: half an iron use still counts as one.
        assertEquals(1, DurabilityScale.damage(IRON_MAX - 0.25, IRON_USES, IRON_MAX));
        assertEquals(2, DurabilityScale.damage(IRON_MAX - 0.75, IRON_USES, IRON_MAX));
        assertEquals(1, DurabilityScale.damage(149.9, 150, 150));
    }

    @Test
    void aBrokenHytaleToolIsWornOut() {
        assertEquals(IRON_USES, DurabilityScale.damage(0, IRON_USES, IRON_MAX));
        assertEquals(0.0, DurabilityScale.durability(IRON_USES + 3, IRON_USES, IRON_MAX));
    }

    @Test
    void anUnbreakableItemHasNoDamage() {
        assertEquals(0, DurabilityScale.damage(0, 0, 0));
        assertEquals(0, DurabilityScale.damage(10, 0, 100));
        assertEquals(100.0, DurabilityScale.durability(3, 0, 100));
    }
}
