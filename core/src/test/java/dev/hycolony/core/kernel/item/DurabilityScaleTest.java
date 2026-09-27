package dev.hycolony.core.kernel.item;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class DurabilityScaleTest {
    /** Tool_Pickaxe_Iron: 250 Hytale points for 500 blocks, so one use is 0.5 point. */
    private static final double IRON_MAX = 250;

    private static final int IRON_USES = 500;

    /** The damage of a stack at {@code durability}, its max the item's own. */
    private static int damage(double durability, double max, int uses) {
        return DurabilityScale.damage(durability, max, max, uses);
    }

    @Test
    void everyDamageReadsBackAsTheSameDamage() {
        for (int damage = 0; damage < IRON_USES; damage++) {
            double durability = DurabilityScale.durability(damage, IRON_USES, IRON_MAX);
            assertEquals(damage, damage(durability, IRON_MAX, IRON_USES));
        }
        assertEquals(149.0, DurabilityScale.durability(1, 150, 150));
    }

    @Test
    void aPartlyUsedStepCountsAsAWholeUseSoTheConversionNeverRepairs() {
        // A player's hit costs 0.25 point: half an iron use still counts as one.
        assertEquals(1, damage(IRON_MAX - 0.25, IRON_MAX, IRON_USES));
        assertEquals(2, damage(IRON_MAX - 0.75, IRON_MAX, IRON_USES));
        assertEquals(1, damage(149.9, 150, 150));
    }

    /** Hytale's repair (withRestoredDurability) lowers the stack's own max: the points it lost count as damage. */
    @Test
    void aRepairedStackWithAReducedMaxKeepsItsLostMaxAsDamage() {
        assertEquals(40, DurabilityScale.damage(60, 60, 100, 100));
        assertEquals(60.0, DurabilityScale.durability(40, 100, 100)); // back at the item's max: 60/100, not 100/100
        assertEquals(50, DurabilityScale.damage(50, 60, 100, 100)); // 40 lost to the repair, 10 used since
    }

    @Test
    void anItemWithoutADefaultMaxIsMeasuredAgainstItsStack() {
        assertEquals(10, DurabilityScale.damage(90, 100, 0, 100));
    }

    @Test
    void aBrokenHytaleToolIsWornOut() {
        assertEquals(IRON_USES, damage(0, IRON_MAX, IRON_USES));
        assertEquals(0.0, DurabilityScale.durability(IRON_USES + 3, IRON_USES, IRON_MAX));
    }

    @Test
    void anItemTheCoreDoesNotWearCountsOneUsePerPoint() {
        assertEquals(30, damage(70, 100, 0));
        assertEquals(70.0, DurabilityScale.durability(30, 0, 100));
    }

    @Test
    void anUnbreakableItemHasNoDamage() {
        assertEquals(0, damage(0, 0, 0));
        assertEquals(0, DurabilityScale.damage(0, 0, 100, 150)); // the stack itself is unbreakable
        assertEquals(0.0, DurabilityScale.durability(3, 150, 0));
    }
}
