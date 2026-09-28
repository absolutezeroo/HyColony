package dev.hycolony.core.crafting.module;

import dev.hycolony.core.building.Building;
import dev.hycolony.core.colony.Colony;
import dev.hycolony.core.crafting.recipe.CraftingRules.CustomRecipe;
import dev.hycolony.core.crafting.recipe.CraftingSetup;
import dev.hycolony.core.crafting.recipe.Ingredient;
import dev.hycolony.core.crafting.recipe.Recipe;
import dev.hycolony.core.crafting.recipe.RecipeId;
import dev.hycolony.core.crafting.recipe.RecipeSource;
import java.util.List;
import java.util.Optional;

/**
 * The custom recipes a hut gets by its level (MC AbstractCraftingBuildingModule.checkForWorkerSpecificRecipes and
 * CustomRecipe.isValidForBuilding), from its job's {@code custom} list in {@code crafting.json}. For now a custom
 * recipe is an existing Hytale recipe, under its own {@code custom:<id>}.
 *
 * <p>Deviation from MC: a custom recipe has no research to require or exclude (research is not ported) and no
 * {@code mustExist}, which no job uses yet. MC's multi-output recipes, and the clean-up of the classic recipes one
 * replaces, have no Hytale counterpart.
 */
final class CustomRecipes {
    private static final System.Logger LOG = System.getLogger(CustomRecipes.class.getName());

    private CustomRecipes() {}

    /** A listed recipe found to be the custom one, and whether the custom one must take its place. */
    private record Duplicate(RecipeId id, boolean forceReplace) {}

    /**
     * MC checkForWorkerSpecificRecipes: for each custom recipe of the module's job, registered in the colony, adds it
     * at the end of the list while the hut level is within its bounds and no duplicate is listed, and removes it
     * otherwise. A custom recipe naming a Hytale recipe the game does not have is skipped.
     */
    static void check(Colony colony, Building hut, CraftingModule module) {
        CraftingSetup crafting = colony.context().ports().crafting();
        for (CustomRecipe custom : crafting.rules().custom(module.jobId())) {
            Optional<Recipe> hytale = crafting.catalog().byHytaleId(custom.hytaleRecipe());
            if (hytale.isEmpty()) {
                LOG.log(
                        System.Logger.Level.DEBUG,
                        "Custom recipe {0}: no Hytale recipe {1}",
                        custom.id(),
                        custom.hytaleRecipe());
                continue;
            }
            Recipe recipe = hytale.get().withSource(new RecipeSource.Custom(custom.id()));
            RecipeId id = colony.registries().recipes().checkOrAdd(recipe);
            if (isValidFor(custom, hut)) {
                grant(colony, module, id, recipe);
            } else if (module.recipes().contains(id)) {
                module.remove(colony, id);
            }
        }
    }

    /** MC CustomRecipe.isValidForBuilding: the hut level is within the recipe's bounds, both included. */
    static boolean isValidFor(CustomRecipe custom, Building hut) {
        return hut.level() >= custom.minBuildingLevel() && hut.level() <= custom.maxBuildingLevel();
    }

    /**
     * Adds the custom recipe unless a duplicate is listed; puts it in place of a duplicate of the same source whose
     * content changed. Either way wakes the requests its output serves and marks the colony dirty.
     */
    private static void grant(Colony colony, CraftingModule module, RecipeId id, Recipe recipe) {
        Optional<Duplicate> duplicate = findDuplicate(colony, module, id, recipe);
        if (duplicate.isEmpty()) {
            module.addRecipeToList(id, false);
        } else if (duplicate.get().forceReplace() && !duplicate.get().id().equals(id)) {
            module.replaceRecipe(colony, duplicate.get().id(), id);
        } else {
            return;
        }
        module.handleRecipeUpdate(colony, id);
        colony.markDirty();
    }

    /**
     * MC's duplicate search: the custom recipe itself, or a listed recipe making the same output from the same items
     * (whatever their amounts: it may be an improved form). Deviation from MC: the items are compared as sets; MC sorts
     * both lists by a hash of item and amount and compares them pairwise, which can miss a match when amounts differ.
     */
    private static Optional<Duplicate> findDuplicate(Colony colony, CraftingModule module, RecipeId id, Recipe recipe) {
        for (RecipeId listed : module.recipes()) {
            if (listed.equals(id)) {
                return Optional.of(new Duplicate(listed, false));
            }
            Optional<Recipe> storage = colony.registries().recipes().get(listed);
            if (storage.isPresent()
                    && RecipeCompatibility.sameOutput(storage.get(), recipe)
                    && sameItems(storage.get().cleanedInput(), recipe.cleanedInput())) {
                // MC: only a recipe of the same source whose token changed is replaced.
                boolean sameSource = storage.get().source() instanceof RecipeSource.Custom
                        && storage.get().source().equals(recipe.source());
                return Optional.of(new Duplicate(listed, sameSource));
            }
        }
        return Optional.empty();
    }

    /** Whether both cleaned inputs ask for the same items, types or tags, whatever the amounts. */
    private static boolean sameItems(List<Ingredient> a, List<Ingredient> b) {
        return a.size() == b.size() && a.stream().allMatch(x -> b.stream().anyMatch(x::sameThingAs));
    }
}
