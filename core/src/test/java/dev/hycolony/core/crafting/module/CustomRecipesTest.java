package dev.hycolony.core.crafting.module;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.hycolony.core.crafting.recipe.BenchRequirement;
import dev.hycolony.core.crafting.recipe.Ingredient;
import dev.hycolony.core.crafting.recipe.Recipe;
import dev.hycolony.core.crafting.recipe.RecipeFixtures;
import dev.hycolony.core.crafting.recipe.RecipeId;
import dev.hycolony.core.crafting.recipe.RecipeSource;
import dev.hycolony.core.kernel.item.ItemAmount;
import dev.hycolony.core.kernel.item.ItemKey;
import dev.hycolony.core.request.Resolver;
import dev.hycolony.core.request.model.RequestToken;
import dev.hycolony.core.request.model.StackRequest;
import dev.hycolony.core.request.resolver.PlayerResolver;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class CustomRecipesTest {
    private static final ItemKey FIBRE = new ItemKey("Ingredient_Fibre");
    private static final ItemKey WHEAT_SEEDS = new ItemKey("Plant_Seeds_Wheat");
    private static final RecipeId CUSTOM = new RecipeId("custom:farmer_wheat_seeds");
    private static final String RULES = """
            {"jobs": {"%s": {
                "allow": [{"bench": "Farmingbench", "categories": ["*"]}, {"bench": "Fieldcraft", "categories": ["*"]}],
                "custom": [{"id": "farmer_wheat_seeds", "hytaleRecipe": "Seeds_From_Essence",
                            "minBuildingLevel": 1, "maxBuildingLevel": 2}]}}}""".formatted(CraftingHut.JOB);
    /** The Hytale recipe the custom one names: 2 essence and 1 fibre make 1 wheat seed, by hand. */
    private static final Recipe HYTALE = recipe("Seeds_From_Essence", 2, 1);

    private final CraftingHut h = new CraftingHut(RULES, true);

    CustomRecipesTest() {
        h.t.recipes.add(HYTALE);
        h.hut.setLevel(0);
    }

    private static Recipe recipe(String hytaleId, int essence, int fibre) {
        return new Recipe(
                List.of(new Ingredient.OfItem(RecipeFixtures.ESSENCE, essence), new Ingredient.OfItem(FIBRE, fibre)),
                new ItemAmount(WHEAT_SEEDS, 1),
                List.of(),
                new BenchRequirement(BenchRequirement.FIELDCRAFT, List.of("Seeds"), 0),
                Optional.empty(),
                new RecipeSource.Hytale(hytaleId),
                false);
    }

    private void check() {
        CustomRecipes.check(h.colony, h.hut, h.module);
    }

    @Test
    void customRecipeIsGrantedFromItsMinimumLevel() {
        RecipeId taught = h.teach(RecipeFixtures.fieldcraft("Basic", "Torch"));
        check();
        assertEquals(List.of(taught), h.module.recipes(), "level 0 is below the minimum");

        h.hut.setLevel(1);
        h.colony.clearDirty();
        check();

        assertEquals(List.of(taught, CUSTOM), h.module.recipes());
        assertTrue(h.module.isCustom(h.colony, CUSTOM));
        assertTrue(h.colony.registries().recipes().get(CUSTOM).orElseThrow().sameContentAs(HYTALE));
        assertTrue(h.colony.isDirty());
    }

    @Test
    void grantingTwiceKeepsOneEntry() {
        h.hut.setLevel(1);
        check();
        check();

        assertEquals(List.of(CUSTOM), h.module.recipes());
    }

    @Test
    void customRecipeIsRemovedAboveItsMaximumLevel() {
        h.hut.setLevel(2);
        check();
        assertEquals(List.of(CUSTOM), h.module.recipes());

        h.hut.setLevel(3);
        check();

        assertEquals(List.of(), h.module.recipes());
    }

    @Test
    void customRecipeIsNotGrantedOverAPlayerTaughtDuplicate() {
        h.hut.setLevel(1);
        RecipeId taught = h.teach(recipe("Seeds_Taught", 3, 1));

        check();

        assertEquals(List.of(taught), h.module.recipes(), "MC: same output and items, taught (no source): kept");
    }

    @Test
    void improvedCustomRecipeIsNotGrantedAgain() {
        h.hut.setLevel(1);
        check();
        RecipeId improved = h.register(HYTALE.improvedWith(
                List.of(new Ingredient.OfItem(RecipeFixtures.ESSENCE, 1), new Ingredient.OfItem(FIBRE, 1))));
        h.module.replaceRecipe(h.colony, CUSTOM, improved);

        check();

        assertEquals(List.of(improved), h.module.recipes(), "MC: the recipe exists in an improved form");
    }

    @Test
    void customRecipeIsGrantedNextToATaughtOneWithOtherItems() {
        h.hut.setLevel(1);
        RecipeId taught = h.teach(RecipeFixtures.fieldcraft("Seeds", "Plant_Seeds_Wheat"));

        check();

        assertEquals(List.of(taught, CUSTOM), h.module.recipes());
    }

    @Test
    void grantingWakesRequestsForItsOutput() {
        RequestToken wheat =
                h.colony.requests().createAndAssign(h.hut, new StackRequest(WHEAT_SEEDS, 10, 10, true), -1);
        h.hut.setLevel(1);

        check();

        assertEquals(
                PlayerResolver.ID,
                h.colony.requests().resolverOf(wheat).map(Resolver::resolverId).orElseThrow());
    }

    @Test
    void customRecipeTheGameNoLongerHasIsSkipped() {
        h.t.recipes.recipes.clear();
        h.hut.setLevel(1);

        check();

        assertEquals(List.of(), h.module.recipes());
        assertTrue(h.colony.registries().recipes().get(CUSTOM).isEmpty());
    }

    @Test
    void customRecipeDoesNotCountTowardsTheMaximum() {
        h.hut.setLevel(1);
        check();

        assertEquals(0, h.module.activeRecipes(h.colony));
    }

    @Test
    void colonyTickGrantsCustomRecipes() {
        h.hut.setLevel(1);

        h.colony.buildings().onColonyTick(h.colony);

        assertEquals(List.of(CUSTOM), h.module.recipes());
        assertFalse(h.module.isDisabled(CUSTOM));
    }
}
