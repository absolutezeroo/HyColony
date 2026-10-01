package dev.hycolony.core.crafting.restaurant;

import dev.hycolony.core.colony.Colony;
import dev.hycolony.core.crafting.recipe.Ingredient;
import dev.hycolony.core.crafting.recipe.Recipe;
import dev.hycolony.core.crafting.recipe.RecipeCatalog;
import dev.hycolony.core.kernel.item.ItemAmount;
import dev.hycolony.core.kernel.item.ItemKey;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * The ingredients of a dish, down its recipes (MC RestaurantMenuModuleWindow.getRecipeFromStack and processRecipe,
 * at most {@link #MAX_DEPTH} recipes deep): the first recipe that makes it, else the raw item that cooks into it, one
 * for one (MC finds the furnace recipe by the item's id). An input without a recipe is an ingredient, as many as its
 * recipe asks (MC does not scale a nested recipe's inputs).
 */
final class MenuIngredients {
    /** MC getRecipeFromStack's maxDepth. */
    static final int MAX_DEPTH = 5;

    private MenuIngredients() {}

    /** What {@link #of} gives: the leaf ingredients of one run, and the dishes that run makes (at least 1). */
    record Result(List<ItemAmount> ingredients, int made) {}

    /** One way to make an item: its inputs, and how many one run makes. */
    private record Making(List<ItemAmount> inputs, int made) {}

    /** The ingredients of {@code dish}; none for a dish nothing makes. */
    static Result of(Colony colony, ItemKey dish) {
        List<ItemAmount> out = new ArrayList<>();
        Optional<Making> making = making(colony, dish);
        making.ifPresent(m -> expand(colony, m, out, 1));
        return new Result(out, making.map(Making::made).orElse(1));
    }

    /** MC processRecipe's loop: each input with a recipe is expanded, the others are ingredients. */
    private static void expand(Colony colony, Making making, List<ItemAmount> out, int depth) {
        for (ItemAmount in : making.inputs()) {
            Optional<Making> inner = depth > MAX_DEPTH ? Optional.empty() : making(colony, in.item());
            if (inner.isPresent()) {
                expand(colony, inner.get(), out, depth + 1);
            } else {
                out.add(in);
            }
        }
    }

    /** The first recipe making {@code item}, else cooking its raw item; empty for an item nothing makes. */
    private static Optional<Making> making(Colony colony, ItemKey item) {
        RecipeCatalog recipes = colony.context().ports().crafting().catalog();
        Optional<Recipe> crafted = recipes.all().stream()
                .filter(r -> r.primaryOutput().item().equals(item))
                .findFirst();
        if (crafted.isPresent()) {
            List<ItemAmount> inputs = new ArrayList<>();
            for (Ingredient in : crafted.get().inputs()) {
                firstItem(recipes, in).ifPresent(i -> inputs.add(new ItemAmount(i, in.amount())));
            }
            return Optional.of(
                    new Making(inputs, Math.max(1, crafted.get().primaryOutput().count())));
        }
        return colony.context()
                .ports()
                .cooking()
                .catalog()
                .rawFor(item)
                .map(raw -> new Making(List.of(new ItemAmount(raw, 1)), 1));
    }

    /** The item an ingredient stands for: itself, or the first of its type or tag (MC ingredient.getItems()[0]). */
    private static Optional<ItemKey> firstItem(RecipeCatalog recipes, Ingredient in) {
        if (in instanceof Ingredient.OfItem of) {
            return Optional.of(of.item());
        }
        return recipes.itemsOf(in).stream().findFirst();
    }
}
