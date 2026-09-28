package dev.hycolony.core.crafting.module;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.hycolony.core.crafting.module.CraftingModule.LearnRefusal;
import dev.hycolony.core.crafting.recipe.BenchRequirement;
import dev.hycolony.core.crafting.recipe.Ingredient;
import dev.hycolony.core.crafting.recipe.Recipe;
import dev.hycolony.core.crafting.recipe.RecipeFixtures;
import dev.hycolony.core.crafting.recipe.RecipeId;
import dev.hycolony.core.crafting.recipe.RecipeSource;
import dev.hycolony.core.kernel.item.ItemAmount;
import dev.hycolony.core.kernel.item.ItemKey;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class RecipeCompatibilityTest {
    private static final Recipe WHEAT = RecipeFixtures.at("Farmingbench", "Seeds", "Plant_Seeds_Wheat");

    private final CraftingHut h = new CraftingHut();

    private static Recipe withBench(Recipe r, BenchRequirement bench) {
        return new Recipe(
                r.inputs(),
                r.primaryOutput(),
                r.secondaryOutputs(),
                bench,
                r.requiredTool(),
                r.source(),
                r.knowledgeRequired());
    }

    private static Recipe knowledgeRequired(Recipe r) {
        return new Recipe(
                r.inputs(), r.primaryOutput(), r.secondaryOutputs(), r.bench(), r.requiredTool(), r.source(), true);
    }

    private Optional<LearnRefusal> canLearn(Recipe r) {
        return h.module.canLearn(h.colony, h.hut, h.register(r), h.owner);
    }

    @Test
    void recipeAtABenchOfTheHutIsCompatible() {
        h.bench("Farmingbench", 1);

        assertEquals(Optional.empty(), canLearn(WHEAT));
    }

    @Test
    void recipeAtABenchTheHutLacksIsIncompatible() {
        h.bench("Workbench", 3);

        assertEquals(Optional.of(LearnRefusal.INCOMPATIBLE), canLearn(WHEAT));
    }

    @Test
    void benchOfTooLowTierMakesTheRecipeIncompatible() {
        h.bench("Farmingbench", 1);
        Recipe tierTwo = withBench(WHEAT, new BenchRequirement("Farmingbench", List.of("Seeds"), 2));

        assertEquals(Optional.of(LearnRefusal.INCOMPATIBLE), canLearn(tierTwo));
        assertFalse(h.module.learn(h.colony, h.hut, h.register(tierTwo), h.owner));

        h.bench("Farmingbench", 2);
        assertEquals(Optional.empty(), canLearn(tierTwo));
    }

    @Test
    void benchWithoutTheRecipesCategoryIsIncompatible() {
        h.bench("Farmingbench", 1);
        h.t.recipes.benchCategories.put("Farmingbench", List.of("Plants"));

        assertEquals(Optional.of(LearnRefusal.INCOMPATIBLE), canLearn(WHEAT));

        h.t.recipes.benchCategories.put("Farmingbench", List.of("Plants", "Seeds"));
        assertEquals(Optional.empty(), canLearn(WHEAT));
    }

    @Test
    void fieldcraftRecipeNeedsNoBench() {
        assertTrue(h.hut.registeredBlocks().workstations().isEmpty());

        assertEquals(Optional.empty(), canLearn(RecipeFixtures.fieldcraft("Basic", "Torch")));
    }

    /** Deviation from MC: nobody could bring an ingredient no item answers, so its requests would wait forever. */
    @Test
    void recipeWithAnIngredientNoItemAnswersIsIncompatible() {
        Recipe byTag = new Recipe(
                List.of(new Ingredient.OfTag("Type=Essence", 2)),
                new ItemAmount(new ItemKey("Plant_Seeds_Wheat"), 1),
                List.of(),
                new BenchRequirement(BenchRequirement.FIELDCRAFT, List.of("Seeds"), 0),
                Optional.empty(),
                new RecipeSource.Hytale("Seeds_By_Tag"),
                false);

        assertEquals(Optional.of(LearnRefusal.INCOMPATIBLE), canLearn(byTag));

        h.t.recipes.tag("Type=Essence", RecipeFixtures.ESSENCE);
        assertEquals(Optional.empty(), canLearn(byTag));
    }

    @Test
    void recipeTheJobMayNotLearnIsIncompatible() {
        h.bench("Workbench", 1);

        assertEquals(Optional.of(LearnRefusal.INCOMPATIBLE), canLearn(RecipeFixtures.at("Workbench", "Tools", "Hoe")));
    }

    @Test
    void recipeUnknownToTheRegistryIsIncompatible() {
        assertEquals(
                Optional.of(LearnRefusal.INCOMPATIBLE),
                h.module.canLearn(h.colony, h.hut, new RecipeId("hytale:Gone"), h.owner));
    }

    @Test
    void recipeReservedToKnowingPlayersNeedsAPlayerWhoKnowsIt() {
        h.bench("Farmingbench", 1);
        RecipeId id = h.register(knowledgeRequired(WHEAT));
        UUID knowing = UUID.randomUUID();
        h.t.recipes.knownByPlayer.put(knowing, Set.of("Plant_Seeds_Wheat"));

        assertEquals(Optional.of(LearnRefusal.UNKNOWN_TO_PLAYER), h.module.canLearn(h.colony, h.hut, id, h.owner));
        assertFalse(h.module.learn(h.colony, h.hut, id, h.owner));
        assertTrue(h.module.learn(h.colony, h.hut, id, knowing));
        assertEquals(List.of(id), h.module.recipes());
    }
}
