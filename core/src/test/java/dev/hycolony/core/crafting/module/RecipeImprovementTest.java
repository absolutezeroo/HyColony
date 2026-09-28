package dev.hycolony.core.crafting.module;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.hycolony.core.citizen.CitizenData;
import dev.hycolony.core.citizen.Skill;
import dev.hycolony.core.crafting.module.RecipeImprovement.Crafted;
import dev.hycolony.core.crafting.recipe.BenchRequirement;
import dev.hycolony.core.crafting.recipe.Ingredient;
import dev.hycolony.core.crafting.recipe.Recipe;
import dev.hycolony.core.crafting.recipe.RecipeFixtures;
import dev.hycolony.core.crafting.recipe.RecipeId;
import dev.hycolony.core.crafting.recipe.RecipeSource;
import dev.hycolony.core.kernel.BlockPos;
import dev.hycolony.core.kernel.item.ItemAmount;
import dev.hycolony.core.kernel.item.ItemKey;
import dev.hycolony.core.kernel.port.Msg;
import dev.hycolony.core.testing.FakeNotifier;
import java.util.List;
import java.util.Optional;
import java.util.random.RandomGenerator;
import org.junit.jupiter.api.Test;

class RecipeImprovementTest {
    private static final ItemKey FIBRE = new ItemKey("Ingredient_Fibre");
    private static final ItemKey STONE = new ItemKey("Rock_Stone");
    private static final ItemKey OAK = new ItemKey("Wood_Oak_Trunk");
    private static final ItemKey BIRCH = new ItemKey("Wood_Birch_Trunk");
    private static final ItemKey SEEDS = new ItemKey("Plant_Seeds_Wheat");
    /** Always rolls 0 and picks message 0: every improvement chance succeeds. */
    private static final RandomGenerator LUCKY = () -> 0L;
    /** Rolls just under 100: no improvement chance up to 5 % succeeds. */
    private static final RandomGenerator UNLUCKY = () -> -1L;

    private final CraftingHut h = new CraftingHut(rules("[]"), true);
    private final CitizenData crafter = h.hire();

    private static String rules(String excludedProducts) {
        return """
                {"jobs": {"%s": {"allow": [{"bench": "Fieldcraft", "categories": ["*"]}]}},
                 "reduceable": {"ingredients": ["Ingredient_Life_Essence", "Ingredient_Fibre",
                                                "Wood_Oak_Trunk", "Wood_Birch_Trunk"],
                                "excludedProducts": %s}}""".formatted(CraftingHut.JOB, excludedProducts);
    }

    /** By hand, {@code inputs} make one wheat seed; the Hytale recipe {@code Seeds}. */
    private static Recipe recipe(Ingredient... inputs) {
        return recipe("Seeds", inputs);
    }

    private static Recipe recipe(String hytaleId, Ingredient... inputs) {
        return new Recipe(
                List.of(inputs),
                new ItemAmount(SEEDS, 1),
                List.of(),
                new BenchRequirement(BenchRequirement.FIELDCRAFT, List.of("Seeds"), 0),
                Optional.empty(),
                new RecipeSource.Hytale(hytaleId),
                false);
    }

    private static Ingredient item(ItemKey key, int amount) {
        return new Ingredient.OfItem(key, amount);
    }

    private void improve(CraftingHut hut, RecipeId id, RandomGenerator random) {
        RecipeImprovement.improve(
                hut.colony, hut.hut, hut.module, new Crafted(id, 1, crafter, Skill.Knowledge), random);
    }

    /** The crafting messages the colony members got. */
    private List<FakeNotifier.Sent> announced() {
        return h.t.notifier.sent.stream()
                .filter(s -> s.msg().key().startsWith("hycolony.crafting."))
                .toList();
    }

    private Recipe listed(int index) {
        return h.colony.recipes().get(h.module.recipes().get(index)).orElseThrow();
    }

    @Test
    void improvementChanceIsCappedAtFivePercent() {
        assertEquals(5.0, RecipeImprovement.chance(1000, 99));
        assertEquals(0.0625, RecipeImprovement.chance(1, 0));
        assertEquals(0.875, RecipeImprovement.chance(10, 4));
    }

    @Test
    void improvementRemovesOneOfEachReduceableIngredientAboveOne() {
        RecipeId id = h.teach(recipe(item(FIBRE, 1), item(RecipeFixtures.ESSENCE, 2), item(STONE, 3)));

        improve(h, id, LUCKY);

        assertEquals(
                List.of(item(STONE, 3), item(RecipeFixtures.ESSENCE, 1), item(FIBRE, 1)),
                listed(0).inputs(),
                "MC: cleaned input sorted by amount, most first; stone is not reduceable, fibre is at 1");
        assertEquals(new RecipeSource.Improved(), listed(0).source());
    }

    @Test
    void nothingHappensWhenTheRollMisses() {
        RecipeId id = h.teach(recipe(item(RecipeFixtures.ESSENCE, 2)));

        improve(h, id, UNLUCKY);

        assertEquals(List.of(id), h.module.recipes());
        assertTrue(announced().isEmpty());
    }

    @Test
    void improvementNeverTouchesAnExcludedProduct() {
        CraftingHut excluded = new CraftingHut(rules("[\"Plant_Seeds_Wheat\"]"), true);
        RecipeId id = excluded.teach(recipe(item(RecipeFixtures.ESSENCE, 2)));

        improve(excluded, id, LUCKY);

        assertEquals(List.of(id), excluded.module.recipes());
        assertTrue(excluded.colony.recipes().get(new RecipeId("improved:1")).isEmpty());
    }

    @Test
    void recipeWithNothingToReduceStaysAsItIs() {
        RecipeId id = h.teach(recipe(item(STONE, 4), item(FIBRE, 1)));

        improve(h, id, LUCKY);

        assertEquals(List.of(id), h.module.recipes());
        assertTrue(h.colony.recipes().get(new RecipeId("improved:1")).isEmpty());
    }

    @Test
    void improvedRecipeTakesThePlaceOfTheOriginal() {
        RecipeId first = h.teach(RecipeFixtures.fieldcraft("Basic", "Torch"));
        RecipeId id = h.teach(recipe(item(RecipeFixtures.ESSENCE, 2)));
        RecipeId last = h.teach(RecipeFixtures.fieldcraft("Basic", "Rope"));
        h.colony.clearDirty();

        improve(h, id, LUCKY);

        assertEquals(List.of(first, new RecipeId("improved:1"), last), h.module.recipes());
        assertTrue(h.colony.isDirty());
    }

    @Test
    void improvementIsAnnouncedToTheColony() {
        RecipeId id = h.teach(recipe(item(RecipeFixtures.ESSENCE, 2)));
        crafter.setName("Ada");

        improve(h, id, LUCKY);

        assertEquals(
                List.of(new FakeNotifier.Sent(
                        h.owner,
                        Msg.of(
                                "hycolony.crafting.improved.0",
                                "%hycolony.ui.job.crafter",
                                "Plant_Seeds_Wheat",
                                "Ingredient_Life_Essence",
                                "Ada"))),
                announced());
    }

    @Test
    void resourceTypeIngredientIsReducedWhenAllItsItemsAre() {
        h.t.recipes.resourceType("Wood_Trunk", OAK, BIRCH);
        RecipeId id = h.teach(recipe(new Ingredient.OfResourceType("Wood_Trunk", 2)));

        improve(h, id, LUCKY);

        assertEquals(
                List.of(new Ingredient.OfResourceType("Wood_Trunk", 1)),
                listed(0).inputs());

        h.t.recipes.resourceType("Rock", STONE, OAK);
        RecipeId rock = h.teach(recipe("Seeds_From_Rock", new Ingredient.OfResourceType("Rock", 2)));
        improve(h, rock, LUCKY);
        assertEquals(
                List.of(new Ingredient.OfResourceType("Rock", 2)), listed(1).inputs());
    }

    @Test
    void improvedRecipeTheHutCannotHoldIsNotSwappedIn() {
        CraftingHut benchHut = new CraftingHut("""
                {"jobs": {"%s": {"allow": [{"bench": "Farmingbench", "categories": ["*"]}]}},
                 "reduceable": {"ingredients": ["Ingredient_Life_Essence"]}}""".formatted(CraftingHut.JOB), true);
        BlockPos bench = benchHut.bench("Farmingbench", 1);
        RecipeId id = benchHut.teach(RecipeFixtures.at("Farmingbench", "Seeds", "Plant_Seeds_Wheat"));
        benchHut.hut.registeredBlocks().removeWorkstation(bench);

        improve(benchHut, id, LUCKY);

        assertTrue(benchHut.colony.recipes().get(new RecipeId("improved:1")).isPresent(), "registered, as in MC");
        assertEquals(List.of(id), benchHut.module.recipes(), "MC: isRecipeCompatibleWithCraftingModule(token)");
    }
}
