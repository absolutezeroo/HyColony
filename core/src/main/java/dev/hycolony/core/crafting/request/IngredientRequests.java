package dev.hycolony.core.crafting.request;

import dev.hycolony.core.crafting.recipe.Ingredient;
import dev.hycolony.core.crafting.recipe.RecipeCatalog;
import dev.hycolony.core.request.model.Deliverable;
import dev.hycolony.core.request.model.StackList;
import dev.hycolony.core.request.model.StackRequest;

/**
 * What the crafting resolvers ask for a recipe ingredient (MC {@code new Stack(ingredient.getItemStack(), count,
 * minCount)}). Deviation from MC: an ingredient given by resource type or tag is asked as a {@link StackList} of its
 * items, described by the type or tag id; MC recipes name an exact item. Its recipe comes from {@code RecipeChoice},
 * which skips a recipe with an ingredient no item answers, so that list is never empty.
 */
final class IngredientRequests {
    private IngredientRequests() {}

    /** {@code count} of the ingredient, at least {@code minCount}; a building may serve it from its own stock. */
    static Deliverable of(Ingredient in, int count, int minCount, RecipeCatalog catalog) {
        return switch (in) {
            case Ingredient.OfItem item -> new StackRequest(item.item(), count, minCount, true);
            case Ingredient.OfResourceType type -> new StackList(catalog.itemsOf(type), type.id(), count, minCount);
            case Ingredient.OfTag tag -> new StackList(catalog.itemsOf(tag), tag.id(), count, minCount);
        };
    }
}
