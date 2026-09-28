package dev.hycolony.core.crafting.module;

import dev.hycolony.core.building.Building;
import dev.hycolony.core.colony.Colony;
import dev.hycolony.core.crafting.recipe.BenchRequirement;
import dev.hycolony.core.crafting.recipe.CraftingRules.CustomRecipe;
import dev.hycolony.core.crafting.recipe.CraftingSetup;
import dev.hycolony.core.crafting.recipe.Recipe;
import dev.hycolony.core.crafting.recipe.RecipeCatalog;
import dev.hycolony.core.crafting.recipe.RecipeId;
import dev.hycolony.core.crafting.recipe.RecipeSource;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Whether a hut's crafting module may hold a recipe (MC AbstractCraftingBuildingModule.isRecipeCompatible and the
 * crafter's own override, e.g. BuildingFarmer.CraftingModule: the intermediate block, then the {@code crafterProduct}
 * tags). Deviation from MC: the intermediate block becomes a bench of the hut's plan, with Hytale's categories and tier,
 * and the tags become the job's filter in {@code crafting.json}.
 */
final class RecipeCompatibility {
    private RecipeCompatibility() {}

    /**
     * Whether the hut has the recipe's bench: always for a Fieldcraft recipe (MC intermediate {@code AIR}); otherwise
     * a registered bench of the same id and a tier at least the recipe's, whose categories (empty = all) hold the
     * recipe's.
     */
    static boolean benchPresent(Building hut, Recipe recipe, RecipeCatalog catalog) {
        BenchRequirement needed = recipe.bench();
        if (needed.isFieldcraft()) {
            return true;
        }
        List<String> categories = catalog.benchCategories(needed.benchId());
        if (!categories.isEmpty() && !categories.containsAll(needed.categories())) {
            return false;
        }
        return hut.registeredBlocks().workstations().values().stream()
                .anyMatch(w -> w.benchId().equals(needed.benchId()) && w.tier() >= needed.requiredTier());
    }

    /** MC isRecipeCompatibleWithCraftingModule: the hut has its bench and the job may learn it. */
    static boolean compatible(Colony colony, Building hut, String jobId, Recipe recipe) {
        CraftingSetup crafting = colony.context().ports().crafting();
        return benchPresent(hut, recipe, crafting.catalog()) && crafting.rules().allows(jobId, recipe);
    }

    /**
     * Whether {@code player} may teach the recipe. Deviation from MC: a Hytale recipe reserved to players who learnt
     * it ({@code KnowledgeRequired}) needs such a player; MC recipes have no such rule.
     */
    static boolean knownBy(Colony colony, Recipe recipe, UUID player) {
        if (!recipe.knowledgeRequired()) {
            return true;
        }
        return recipe.source() instanceof RecipeSource.Hytale(String hytaleId)
                && colony.context().ports().crafting().catalog().playerKnows(player, hytaleId);
    }

    /**
     * MC AbstractCraftingBuildingModule.serializeToView's test to keep a listed recipe: it is still in the registry,
     * a custom one is still in {@code crafting.json}, and it is compatible or pre-taught. Deviation from MC: a recipe
     * failing it is no longer chosen but stays listed, where MC drops it from the list when its view is refreshed.
     */
    static boolean stillValid(Colony colony, Building hut, String jobId, RecipeId id) {
        Optional<Recipe> recipe = colony.recipes().get(id);
        if (recipe.isEmpty()) {
            return false;
        }
        List<CustomRecipe> custom = colony.context().ports().crafting().rules().custom(jobId);
        if (recipe.get().source() instanceof RecipeSource.Custom(String customId)
                && custom.stream().noneMatch(c -> c.id().equals(customId))) {
            return false;
        }
        return compatible(colony, hut, jobId, recipe.get()) || isPreTaught(colony, recipe.get(), custom);
    }

    /** MC isPreTaughtRecipe: a custom recipe of the job makes the same item, as many of it. */
    private static boolean isPreTaught(Colony colony, Recipe recipe, List<CustomRecipe> custom) {
        RecipeCatalog catalog = colony.context().ports().crafting().catalog();
        return custom.stream()
                .flatMap(c -> catalog.byHytaleId(c.hytaleRecipe()).stream())
                .anyMatch(c -> sameOutput(c, recipe));
    }

    /** Whether both make the same item, as many of it (MC compareItemStacksIgnoreStackSize and the same count). */
    static boolean sameOutput(Recipe a, Recipe b) {
        return a.primaryOutput().item().equals(b.primaryOutput().item())
                && a.primaryOutput().count() == b.primaryOutput().count();
    }
}
