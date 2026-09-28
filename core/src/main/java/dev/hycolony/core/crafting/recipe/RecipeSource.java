package dev.hycolony.core.crafting.recipe;

import java.util.Objects;

/**
 * Where a recipe comes from (MC RecipeStorage.recipeSource): a Hytale recipe a player taught the hut, a custom recipe
 * of {@code crafting.json} granted by the hut level (MC CustomRecipe), or a recipe a crafter improved (MC
 * AbstractCraftingBuildingModule.improveRecipe, no source).
 */
public sealed interface RecipeSource {
    /** A Hytale recipe, by its Hytale id. */
    record Hytale(String id) implements RecipeSource {
        public Hytale {
            Objects.requireNonNull(id, "id");
        }
    }

    /** A custom recipe, by its {@code crafting.json} id. */
    record Custom(String id) implements RecipeSource {
        public Custom {
            Objects.requireNonNull(id, "id");
        }
    }

    /** A recipe a crafter improved. */
    record Improved() implements RecipeSource {}
}
