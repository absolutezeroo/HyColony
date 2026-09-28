package dev.hycolony.core.request.model;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

import dev.hycolony.core.kernel.item.ItemKey;
import org.junit.jupiter.api.Test;

class CraftingTest {
    private static final ItemKey SEEDS = new ItemKey("Plant_Seeds_Wheat");

    @Test
    void craftingEqualityIgnoresTheRecipe() {
        Crafting a = new Crafting(SEEDS, 3, 1, "a", true);
        Crafting b = new Crafting(SEEDS, 3, 1, "b", false);

        assertEquals(a, b, "MC AbstractCrafting.equals: stack, count and minCount only");
        assertEquals(a.hashCode(), b.hashCode());
        assertNotEquals(a, new Crafting(SEEDS, 4, 1, "a", true));
        assertNotEquals(a, new Crafting(SEEDS, 3, 2, "a", true));
        assertNotEquals(a, new Crafting(new ItemKey("Plant_Seeds_Corn"), 3, 1, "a", true));
    }

    @Test
    void describesTheRunsAndTheOutput() {
        assertEquals("3 x craft Plant_Seeds_Wheat", new Crafting(SEEDS, 3, 1, "a", true).describe());
    }
}
