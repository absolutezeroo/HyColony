package dev.hycolony.core.crafting.job;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class CraftingProgressTest {
    /** MC getRequiredProgressForMakingRawMaterial: 10 / min(skill / 2 + 1, 50) * 3, in integer arithmetic. */
    @Test
    void requiredHitsFollowsMcIntegerDivision() {
        assertEquals(30, CraftingProgress.requiredHits(0));
        assertEquals(30, CraftingProgress.requiredHits(1));
        assertEquals(15, CraftingProgress.requiredHits(2));
        assertEquals(3, CraftingProgress.requiredHits(10));
        assertEquals(0, CraftingProgress.requiredHits(30));
        assertEquals(0, CraftingProgress.requiredHits(200), "the level stops counting at 50");
    }
}
