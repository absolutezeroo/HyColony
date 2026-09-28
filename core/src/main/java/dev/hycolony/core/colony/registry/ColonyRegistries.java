package dev.hycolony.core.colony.registry;

import dev.hycolony.core.crafting.recipe.RecipeRegistry;
import dev.hycolony.core.farming.field.FieldRegistry;

/**
 * The colony-wide registries its features keep (MC IColonyManager.getRecipeManager, per colony here, and
 * RegisteredStructureManager's building extensions): the recipes its huts learnt, and its fields. Grouped so that
 * {@code Colony} stays within its size limits.
 */
public final class ColonyRegistries {
    private final RecipeRegistry recipes = new RecipeRegistry();
    private final FieldRegistry fields = new FieldRegistry();

    /** The recipes the colony's huts learnt or improved. */
    public RecipeRegistry recipes() {
        return recipes;
    }

    /** The colony's fields. */
    public FieldRegistry fields() {
        return fields;
    }
}
