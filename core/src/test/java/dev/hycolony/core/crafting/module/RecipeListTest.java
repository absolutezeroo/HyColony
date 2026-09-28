package dev.hycolony.core.crafting.module;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.hycolony.core.crafting.recipe.RecipeId;
import java.util.List;
import org.junit.jupiter.api.Test;

class RecipeListTest {
    private static final RecipeId A = new RecipeId("hytale:A");
    private static final RecipeId B = new RecipeId("hytale:B");
    private static final RecipeId C = new RecipeId("hytale:C");

    private final RecipeList list = new RecipeList();

    @Test
    void addNeverListsAnIdTwice() {
        assertTrue(list.add(A, false));
        assertTrue(list.add(B, true));

        assertFalse(list.add(A, true));
        assertEquals(List.of(B, A), list.ids());
    }

    @Test
    void replaceTakesThePlaceOfTheOld() {
        list.add(A, false);
        list.add(B, false);
        list.add(C, false);

        assertTrue(list.replace(B, new RecipeId("improved:1")));

        assertEquals(List.of(A, new RecipeId("improved:1"), C), list.ids());
    }

    @Test
    void replaceByAnAlreadyListedIdOnlyRemovesTheOld() {
        list.add(A, false);
        list.add(B, false);
        list.add(C, false);

        assertTrue(list.replace(A, C));

        assertEquals(List.of(B, C), list.ids());
    }

    @Test
    void replaceOfAnUnlistedIdChangesNothing() {
        list.add(A, false);

        assertFalse(list.replace(B, C));

        assertEquals(List.of(A), list.ids());
    }

    @Test
    void toggleTellsWhetherTheRecipeIsEnabledAgain() {
        list.add(A, false);

        assertFalse(list.toggle(A));
        assertTrue(list.isDisabled(A));
        assertTrue(list.toggle(A));
        assertFalse(list.isDisabled(A));
    }
}
