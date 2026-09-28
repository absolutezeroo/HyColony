package dev.hycolony.core.crafting.recipe;

import dev.hycolony.core.kernel.item.ItemKey;
import java.util.List;

/**
 * Whether an item answers a recipe ingredient (MC ItemStorage.equals against a stack). Deviation from MC: a resource
 * type or tag ingredient accepts every item the catalog lists for it.
 */
public final class RecipeMatching {
    private RecipeMatching() {}

    /** Whether {@code item} may be used as {@code ingredient}; false for a type or tag the catalog does not know. */
    public static boolean accepts(Ingredient ingredient, ItemKey item, RecipeCatalog catalog) {
        if (ingredient instanceof Ingredient.OfItem of) {
            return of.item().equals(item);
        }
        return catalog.itemsOf(ingredient).contains(item);
    }

    /** The items {@link #accepts} takes for {@code ingredient}; empty for a type or tag the catalog does not know. */
    public static List<ItemKey> items(Ingredient ingredient, RecipeCatalog catalog) {
        if (ingredient instanceof Ingredient.OfItem of) {
            return List.of(of.item());
        }
        return catalog.itemsOf(ingredient);
    }
}
