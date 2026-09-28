package dev.hycolony.core.crafting.request;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.hycolony.core.crafting.module.RecipeChoice.Chosen;
import dev.hycolony.core.crafting.recipe.BenchRequirement;
import dev.hycolony.core.crafting.recipe.Ingredient;
import dev.hycolony.core.crafting.recipe.Recipe;
import dev.hycolony.core.crafting.recipe.RecipeFixtures;
import dev.hycolony.core.crafting.recipe.RecipeId;
import dev.hycolony.core.crafting.recipe.RecipeSource;
import dev.hycolony.core.kernel.item.ItemAmount;
import dev.hycolony.core.kernel.item.ItemKey;
import dev.hycolony.core.request.model.Crafting;
import dev.hycolony.core.testing.FakeCatalog;
import dev.hycolony.core.testing.crafting.FakeRecipeCatalog;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class CraftingBatchesTest {
    private static final ItemKey SEEDS = new ItemKey("Plant_Seeds_Wheat");
    private static final ItemKey BUCKET = new ItemKey("Container_Bucket");
    private static final ItemKey OAK = new ItemKey("Wood_Oak_Trunk");
    private static final RecipeId ID = new RecipeId("hytale:Plant_Seeds_Wheat");

    private final FakeCatalog items = new FakeCatalog();
    private final FakeRecipeCatalog recipes = new FakeRecipeCatalog();
    private final CraftingBatches batches = new CraftingBatches(items, recipes);

    /** {@code inputs} make {@code perRun} seeds, giving back {@code secondary}. */
    private static Chosen seeds(List<Ingredient> inputs, int perRun, List<ItemAmount> secondary) {
        return new Chosen(
                ID,
                new Recipe(
                        inputs,
                        new ItemAmount(SEEDS, perRun),
                        secondary,
                        new BenchRequirement(BenchRequirement.FIELDCRAFT, List.of(), 0),
                        Optional.empty(),
                        new RecipeSource.Hytale(SEEDS.id()),
                        false));
    }

    /** 2 life essence make 1 seed. */
    private static Chosen twoEssencePerSeed() {
        return seeds(List.of(new Ingredient.OfItem(RecipeFixtures.ESSENCE, 2)), 1, List.of());
    }

    private static List<Integer> counts(List<Crafting> tasks) {
        return tasks.stream().map(Crafting::count).toList();
    }

    private static List<Integer> minCounts(List<Crafting> tasks) {
        return tasks.stream().map(Crafting::minCount).toList();
    }

    @Test
    void thousandRunsMakeTwoBatchesThatFitTheInventory() {
        List<Crafting> tasks = batches.split(twoEssencePerSeed(), 1000, 1000, true);

        // MC arithmetic: 1000 runs need 16 + 32 stacks > 24 slots, so the batch shrinks to 1000 * 24 / 48.
        assertEquals(List.of(500, 500), counts(tasks));
        for (Crafting task : tasks) {
            int stacks = (int) Math.ceil(task.count() / 64.0) + (int) Math.ceil(2 * task.count() / 64.0);
            assertTrue(stacks <= 24, "27 slots less one per row of 8");
        }
    }

    @Test
    void smallRequestIsOneBatch() {
        assertEquals(
                List.of(new Crafting(SEEDS, 10, 10, ID.value(), true)),
                batches.split(twoEssencePerSeed(), 10, 10, true));
    }

    @Test
    void batchesKeepTheRecipeAndVisibility() {
        Crafting task = batches.split(twoEssencePerSeed(), 10, 1, false).getFirst();

        assertEquals(ID.value(), task.recipeId());
        assertEquals(false, task.isPublic());
    }

    @Test
    void minimumCountIsSpreadOverTheBatches() {
        assertEquals(List.of(500, 200), minCounts(batches.split(twoEssencePerSeed(), 1000, 700, true)));
        assertEquals(List.of(1, 1), minCounts(batches.split(twoEssencePerSeed(), 1000, 1, true)), "MC: at least 1");
    }

    @Test
    void countsAreRunsOfTheRecipe() {
        Crafting task = batches.split(
                        seeds(List.of(new Ingredient.OfItem(RecipeFixtures.ESSENCE, 1)), 4, List.of()), 10, 5, true)
                .getFirst();

        assertEquals(3, task.count(), "10 seeds at 4 a run: 3 runs");
        assertEquals(2, task.minCount(), "5 seeds at 4 a run: 2 runs");
    }

    @Test
    void ingredientGivenBackTakesOneSlot() {
        Chosen withBucket = seeds(
                List.of(new Ingredient.OfItem(BUCKET, 1), new Ingredient.OfItem(RecipeFixtures.ESSENCE, 2)),
                1,
                List.of(new ItemAmount(BUCKET, 1)));

        // 1000 runs: 16 + 1 + 32 slots, then 489 runs: 8 + 1 + 16, then 469 runs: 8 + 1 + 15 = 24.
        assertEquals(List.of(469, 469, 62), counts(batches.split(withBucket, 1000, 1000, true)));
    }

    @Test
    void resourceTypeIngredientStacksLikeItsFirstItem() {
        recipes.resourceType("Wood_Trunk", OAK, new ItemKey("Wood_Birch_Trunk"));
        items.maxStacks.put(OAK, 16);
        Chosen fromTrunks = seeds(List.of(new Ingredient.OfResourceType("Wood_Trunk", 2)), 1, List.of());

        // 300 runs: 5 + 38 slots, then 167 runs: 3 + 21 = 24.
        assertEquals(List.of(167, 133), counts(batches.split(fromTrunks, 300, 300, true)));
    }

    @Test
    void unknownResourceTypeStacksBySixtyFour() {
        Chosen fromNothing = seeds(List.of(new Ingredient.OfResourceType("Gone", 2)), 1, List.of());

        assertEquals(List.of(500, 500), counts(batches.split(fromNothing, 1000, 1000, true)));
    }

    @Test
    void runTooBigForTheInventoryIsOneRunPerBatch() {
        Chosen huge = seeds(List.of(new Ingredient.OfItem(RecipeFixtures.ESSENCE, 2000)), 1, List.of());

        assertEquals(List.of(1, 1, 1), counts(batches.split(huge, 3, 3, true)), "MC's batch drops to 0: endless");
    }
}
