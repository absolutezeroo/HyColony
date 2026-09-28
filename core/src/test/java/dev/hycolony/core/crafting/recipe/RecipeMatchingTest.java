package dev.hycolony.core.crafting.recipe;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.hycolony.core.kernel.item.ItemAmount;
import dev.hycolony.core.kernel.item.ItemKey;
import dev.hycolony.core.testing.crafting.FakeRecipeCatalog;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class RecipeMatchingTest {
    private static final ItemKey OAK = new ItemKey("Wood_Oak_Trunk");
    private static final ItemKey BIRCH = new ItemKey("Wood_Birch_Trunk");
    private static final ItemKey STONE = new ItemKey("Rock_Stone");

    private final FakeRecipeCatalog catalog = new FakeRecipeCatalog();

    @Test
    void itemIngredientAcceptsOnlyThatItem() {
        Ingredient in = new Ingredient.OfItem(OAK, 1);
        assertTrue(RecipeMatching.accepts(in, OAK, catalog));
        assertFalse(RecipeMatching.accepts(in, BIRCH, catalog));
    }

    @Test
    void resourceTypeAcceptsEveryItemOfThatType() {
        catalog.resourceType("Wood_Trunk", OAK, BIRCH);
        Ingredient in = new Ingredient.OfResourceType("Wood_Trunk", 4);
        assertTrue(RecipeMatching.accepts(in, BIRCH, catalog));
        assertFalse(RecipeMatching.accepts(in, STONE, catalog));
    }

    @Test
    void tagAcceptsEveryTaggedItem() {
        catalog.tag("Type=Wood", OAK);
        Ingredient in = new Ingredient.OfTag("Type=Wood", 1);
        assertTrue(RecipeMatching.accepts(in, OAK, catalog));
        assertFalse(RecipeMatching.accepts(in, BIRCH, catalog));
    }

    @Test
    void unknownResourceTypeAcceptsNothing() {
        assertFalse(RecipeMatching.accepts(new Ingredient.OfResourceType("Gone", 1), OAK, catalog));
    }

    @Test
    void itemsOfAnItemIngredientIsThatItemEvenUnknownToTheCatalog() {
        assertEquals(List.of(OAK), RecipeMatching.items(new Ingredient.OfItem(OAK, 3), RecipeCatalog.NONE));
    }

    @Test
    void itemsOfAResourceTypeAreTheCatalogs() {
        catalog.resourceType("Wood_Trunk", OAK, BIRCH);
        assertEquals(
                List.of(OAK, BIRCH), RecipeMatching.items(new Ingredient.OfResourceType("Wood_Trunk", 1), catalog));
    }

    @Test
    void ingredientGivenBackAsASecondaryOutputIsNeededOnce() {
        ItemKey bucket = new ItemKey("Container_Bucket");
        Recipe recipe = new Recipe(
                List.of(new Ingredient.OfItem(bucket, 1), new Ingredient.OfItem(OAK, 2)),
                new ItemAmount(STONE, 1),
                List.of(new ItemAmount(bucket, 1)),
                new BenchRequirement(BenchRequirement.FIELDCRAFT, List.of(), 0),
                Optional.empty(),
                new RecipeSource.Hytale("Stone"),
                false);

        assertTrue(RecipeMatching.givenBack(recipe, new Ingredient.OfItem(bucket, 1), catalog));
        assertFalse(RecipeMatching.givenBack(recipe, new Ingredient.OfItem(OAK, 2), catalog));
    }
}
