package dev.hycolony.core.crafting.module;

import dev.hycolony.core.building.Building;
import dev.hycolony.core.colony.Colony;
import dev.hycolony.core.crafting.module.CraftingModule.LearnRefusal;
import dev.hycolony.core.crafting.recipe.Recipe;
import dev.hycolony.core.crafting.recipe.RecipeCatalog;
import dev.hycolony.core.crafting.recipe.RecipeId;
import java.util.Optional;
import java.util.UUID;

/**
 * A player's edits of a hut's recipe list from its Recipes tab (MC AddRemoveRecipeMessage, ToggleRecipeMessage and
 * ChangeRecipePriorityMessage). The buttons MC's WindowListRecipes hides or disables are refused here: Remove on a
 * custom recipe, and Enable on a disabled taught recipe while the hut is full. The caller checks the permission.
 */
public final class RecipeEdits {
    private RecipeEdits() {}

    /**
     * MC AddRemoveRecipeMessage, adding: the recipe known under {@code id}, or else the game's recipe a
     * {@code hytale:<id>} names, is registered (MC checkOrAddRecipe) then learnt; returns the refusal otherwise,
     * registering nothing.
     */
    public static Optional<LearnRefusal> learn(
            Colony colony, Building hut, CraftingModule module, RecipeId id, UUID player) {
        RecipeCatalog catalog = colony.context().ports().crafting().catalog();
        Optional<Recipe> recipe =
                colony.recipes().get(id).or(() -> id.hytaleId().flatMap(catalog::byHytaleId));
        Optional<LearnRefusal> refused = module.refusal(colony, hut, recipe.orElse(null), player);
        if (refused.isPresent() || recipe.isEmpty()) {
            return refused;
        }
        RecipeId registered = colony.recipes().checkOrAdd(recipe.get());
        return module.learn(colony, hut, registered, player)
                ? Optional.empty()
                : module.canLearn(colony, hut, registered, player);
    }

    /**
     * MC AddRemoveRecipeMessage, removing: false for a recipe not listed, or custom. Deviation from MC: a custom recipe
     * cannot be removed, where MC's disabled Remove button gives way with Ctrl held to reset a broken built-in recipe,
     * granted again at the next colony tick; a custom recipe is never broken here, its id follows its content.
     */
    public static boolean remove(Colony colony, CraftingModule module, RecipeId id) {
        return !module.isCustom(colony, id) && module.remove(colony, id);
    }

    /**
     * MC ToggleRecipeMessage: disables or enables the recipe at {@code index}; false for an index outside the list,
     * or when {@link #enableRefusal} refuses enabling it.
     */
    public static boolean toggle(Colony colony, Building hut, CraftingModule module, int index) {
        if (index < 0 || index >= module.recipes().size()) {
            return false;
        }
        if (enableRefusal(colony, hut, module, module.recipes().get(index)).isPresent()) {
            return false;
        }
        module.toggle(colony, index);
        return true;
    }

    /**
     * MC ChangeRecipePriorityMessage: moves the recipe at {@code index} one row up or down, or, on a full move (MC
     * Shift held), to the top or the bottom; false for an index outside the list.
     */
    public static boolean move(Colony colony, CraftingModule module, int index, boolean up, boolean fullMove) {
        return module.switchOrder(colony, index, up ? index - 1 : index + 1, fullMove);
    }

    /**
     * MC WindowListRecipes shows a disabled recipe's Enable button for a custom recipe, or while the hut has room: FULL
     * for a disabled taught recipe of a full hut; empty otherwise. MC's server does not check it.
     */
    static Optional<LearnRefusal> enableRefusal(Colony colony, Building hut, CraftingModule module, RecipeId id) {
        boolean hidden = module.isDisabled(id)
                && !module.isCustom(colony, id)
                && module.activeRecipes(colony) >= module.maxRecipes(hut);
        return hidden ? Optional.of(LearnRefusal.FULL) : Optional.empty();
    }
}
