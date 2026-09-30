package dev.hycolony.core.crafting.module;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.hycolony.core.citizen.CitizenData;
import dev.hycolony.core.crafting.module.RecipeChoice.Chosen;
import dev.hycolony.core.crafting.module.RecipeChoice.FulfillQuery;
import dev.hycolony.core.crafting.recipe.BenchRequirement;
import dev.hycolony.core.crafting.recipe.Ingredient;
import dev.hycolony.core.crafting.recipe.Recipe;
import dev.hycolony.core.crafting.recipe.RecipeFixtures;
import dev.hycolony.core.crafting.recipe.RecipeId;
import dev.hycolony.core.crafting.recipe.RecipeSource;
import dev.hycolony.core.kernel.BlockPos;
import dev.hycolony.core.kernel.item.ItemAmount;
import dev.hycolony.core.kernel.item.ItemKey;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Predicate;
import org.junit.jupiter.api.Test;

class RecipeChoiceTest {
    private static final ItemKey WHEAT_SEEDS = new ItemKey("Plant_Seeds_Wheat");
    private static final ItemKey BUCKET = new ItemKey("Container_Bucket");
    private static final ItemKey OAK = new ItemKey("Wood_Oak_Trunk");
    private static final ItemKey BIRCH = new ItemKey("Wood_Birch_Trunk");

    private final CraftingHut h = new CraftingHut();

    private static Predicate<ItemKey> is(ItemKey item) {
        return item::equals;
    }

    /**
     * A Fieldcraft recipe (Hytale id {@code Hand_<output>}) of {@code inputs} making one {@code output}, giving back
     * {@code secondary}.
     */
    private static Recipe recipe(List<Ingredient> inputs, ItemKey output, List<ItemAmount> secondary) {
        return new Recipe(
                inputs,
                new ItemAmount(output, 1),
                secondary,
                new BenchRequirement(BenchRequirement.FIELDCRAFT, List.of("Basic"), 0),
                Optional.empty(),
                new RecipeSource.Hytale("Hand_" + output.id()),
                false);
    }

    private Optional<RecipeId> first(ItemKey output) {
        return RecipeChoice.firstRecipe(h.colony, h.hut, h.module, is(output)).map(Chosen::id);
    }

    private Optional<RecipeId> fulfillable(ItemKey output, FulfillQuery q) {
        return RecipeChoice.firstFulfillable(h.colony, h.hut, h.module, is(output), q)
                .map(Chosen::id);
    }

    private void inHut(ItemKey item, int count) {
        h.t.containers.insert(List.of(h.hut.position()), new ItemAmount(item, count));
    }

    @Test
    void firstRecipeFollowsTheListOrderAndSkipsDisabled() {
        h.bench("Farmingbench", 1);
        RecipeId byHand =
                h.teach(recipe(List.of(new Ingredient.OfItem(RecipeFixtures.ESSENCE, 3)), WHEAT_SEEDS, List.of()));
        RecipeId atBench = h.teach(RecipeFixtures.at("Farmingbench", "Seeds", "Plant_Seeds_Wheat"));
        h.teach(RecipeFixtures.fieldcraft("Basic", "Other"));

        assertEquals(Optional.of(byHand), first(WHEAT_SEEDS));
        Chosen chosen = RecipeChoice.firstRecipe(h.colony, h.hut, h.module, is(WHEAT_SEEDS))
                .orElseThrow();
        assertEquals(h.colony.registries().recipes().get(byHand), Optional.of(chosen.recipe()));

        h.module.toggle(h.colony, 0);
        assertEquals(Optional.of(atBench), first(WHEAT_SEEDS));

        h.module.toggle(h.colony, 1);
        assertEquals(Optional.empty(), first(WHEAT_SEEDS));
    }

    @Test
    void secondaryOutputIsNeverChosenFor() {
        h.teach(recipe(List.of(new Ingredient.OfItem(BUCKET, 1)), WHEAT_SEEDS, List.of(new ItemAmount(BUCKET, 1))));

        assertEquals(Optional.empty(), first(BUCKET), "MC matches primary and alternate outputs only");
    }

    @Test
    void recipeOfABrokenBenchIsNoLongerChosen() {
        BlockPos bench = h.bench("Farmingbench", 1);
        RecipeId id = h.teach(RecipeFixtures.at("Farmingbench", "Seeds", "Plant_Seeds_Wheat"));
        inHut(RecipeFixtures.ESSENCE, 2);
        assertEquals(Optional.of(id), first(WHEAT_SEEDS));

        h.hut.registeredBlocks().removeWorkstation(bench);

        assertEquals(Optional.empty(), first(WHEAT_SEEDS));
        assertEquals(Optional.empty(), fulfillable(WHEAT_SEEDS, FulfillQuery.of(1)));
        assertEquals(List.of(id), h.module.recipes(), "kept in the list, as MC until its view is refreshed");
    }

    @Test
    void customRecipeIsChosenEvenIfTheJobMayNotLearnIt() {
        CraftingHut gifted = new CraftingHut("""
                {"jobs": {"%s": {"custom": [{"id": "gift", "hytaleRecipe": "Plant_Seeds_Wheat"}]}}}
                """.formatted(CraftingHut.JOB), true);
        Recipe hytale = RecipeFixtures.fieldcraft("Seeds", "Plant_Seeds_Wheat");
        gifted.t.recipes.add(hytale);
        RecipeId custom = gifted.register(RecipeFixtures.from(hytale, new RecipeSource.Custom("gift")));
        gifted.module.addRecipeToList(custom, false);

        assertEquals(
                Optional.of(custom),
                RecipeChoice.firstRecipe(gifted.colony, gifted.hut, gifted.module, is(WHEAT_SEEDS))
                        .map(Chosen::id),
                "MC serializeToView keeps a pre-taught recipe");
    }

    /** A game update left a tag of the recipe with no item: even a pre-taught recipe is no longer chosen. */
    @Test
    void recipeWhoseIngredientNoLongerHasAnyItemIsNoLongerChosen() {
        CraftingHut gifted = new CraftingHut("""
                {"jobs": {"%s": {"custom": [{"id": "gift", "hytaleRecipe": "Hand_Plant_Seeds_Wheat"}]}}}
                """.formatted(CraftingHut.JOB), true);
        Recipe hytale = recipe(List.of(new Ingredient.OfTag("Type=Essence", 2)), WHEAT_SEEDS, List.of());
        gifted.t.recipes.add(hytale).tag("Type=Essence", RecipeFixtures.ESSENCE);
        RecipeId custom = gifted.register(RecipeFixtures.from(hytale, new RecipeSource.Custom("gift")));
        gifted.module.addRecipeToList(custom, false);
        Predicate<ItemKey> seeds = is(WHEAT_SEEDS);
        assertEquals(
                Optional.of(custom),
                RecipeChoice.firstRecipe(gifted.colony, gifted.hut, gifted.module, seeds)
                        .map(Chosen::id));

        gifted.t.recipes.tags.remove("Type=Essence");

        assertEquals(Optional.empty(), RecipeChoice.firstRecipe(gifted.colony, gifted.hut, gifted.module, seeds));
        assertEquals(List.of(custom), gifted.module.recipes(), "kept in the list, as any recipe no longer valid");
    }

    @Test
    void customRecipeDroppedFromCraftingJsonIsNoLongerChosen() {
        RecipeId custom = h.register(RecipeFixtures.from(
                RecipeFixtures.fieldcraft("Seeds", "Plant_Seeds_Wheat"), new RecipeSource.Custom("gone")));
        h.module.addRecipeToList(custom, false);

        assertEquals(Optional.empty(), first(WHEAT_SEEDS));
    }

    @Test
    void fulfillableNeedsTheIngredientsForEveryRun() {
        RecipeId id = h.teach(RecipeFixtures.fieldcraft("Seeds", "Plant_Seeds_Wheat"));
        inHut(RecipeFixtures.ESSENCE, 5);

        assertEquals(Optional.of(id), fulfillable(WHEAT_SEEDS, FulfillQuery.of(2)));
        assertEquals(Optional.empty(), fulfillable(WHEAT_SEEDS, FulfillQuery.of(3)), "3 runs x 2 essence > 5");
    }

    @Test
    void workersInventoriesCountToo() {
        RecipeId id = h.teach(RecipeFixtures.fieldcraft("Seeds", "Plant_Seeds_Wheat"));
        CitizenData worker = h.hire();
        inHut(RecipeFixtures.ESSENCE, 3);
        worker.inventory().insert(new ItemAmount(RecipeFixtures.ESSENCE, 3), item -> 64);

        assertEquals(Optional.of(id), fulfillable(WHEAT_SEEDS, FulfillQuery.of(3)));
    }

    @Test
    void reservedIngredientsAreNotAvailable() {
        RecipeId id = h.teach(RecipeFixtures.fieldcraft("Seeds", "Plant_Seeds_Wheat"));
        inHut(RecipeFixtures.ESSENCE, 5);
        Map<Ingredient, Integer> reserved = Map.of(new Ingredient.OfItem(RecipeFixtures.ESSENCE, 1), 2);

        assertEquals(Optional.empty(), fulfillable(WHEAT_SEEDS, new FulfillQuery(2, reserved)));
        assertEquals(Optional.of(id), fulfillable(WHEAT_SEEDS, new FulfillQuery(1, reserved)));
    }

    @Test
    void ingredientGivenBackIsNeededOnlyOnce() {
        RecipeId id = h.teach(recipe(
                List.of(new Ingredient.OfItem(BUCKET, 1), new Ingredient.OfItem(RecipeFixtures.ESSENCE, 1)),
                WHEAT_SEEDS,
                List.of(new ItemAmount(BUCKET, 1))));
        inHut(BUCKET, 1);
        inHut(RecipeFixtures.ESSENCE, 4);

        assertEquals(Optional.of(id), fulfillable(WHEAT_SEEDS, FulfillQuery.of(4)));
    }

    @Test
    void anyItemOfAResourceTypeCounts() {
        h.t.recipes.resourceType("Wood_Trunk", OAK, BIRCH);
        RecipeId id = h.teach(recipe(List.of(new Ingredient.OfResourceType("Wood_Trunk", 4)), WHEAT_SEEDS, List.of()));
        inHut(OAK, 2);
        inHut(BIRCH, 2);

        assertEquals(Optional.of(id), fulfillable(WHEAT_SEEDS, FulfillQuery.of(1)));
        assertFalse(RecipeChoice.canFullFill(
                h.colony, h.hut, h.colony.registries().recipes().get(id).orElseThrow(), FulfillQuery.of(2)));
        assertTrue(RecipeChoice.canFullFill(
                h.colony, h.hut, h.colony.registries().recipes().get(id).orElseThrow(), FulfillQuery.of(1)));
    }
}
