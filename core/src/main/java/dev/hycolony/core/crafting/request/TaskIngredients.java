package dev.hycolony.core.crafting.request;

import dev.hycolony.core.building.Building;
import dev.hycolony.core.colony.Colony;
import dev.hycolony.core.crafting.module.CraftingModule;
import dev.hycolony.core.crafting.module.RecipeChoice;
import dev.hycolony.core.crafting.module.RecipeChoice.Chosen;
import dev.hycolony.core.crafting.module.RecipeChoice.FulfillQuery;
import dev.hycolony.core.crafting.module.RecipeReservations;
import dev.hycolony.core.crafting.recipe.Ingredient;
import dev.hycolony.core.crafting.recipe.Recipe;
import dev.hycolony.core.crafting.recipe.RecipeCatalog;
import dev.hycolony.core.crafting.recipe.RecipeMatching;
import dev.hycolony.core.kernel.item.ItemKey;
import dev.hycolony.core.request.model.Crafting;
import dev.hycolony.core.request.model.Requestable;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.function.Predicate;

/**
 * What a crafting task asks for before it can be made (MC AbstractCraftingProductionResolver
 * attemptResolveForBuildingAndStack and createRequestsForRecipe).
 */
final class TaskIngredients {
    private TaskIngredients() {}

    /**
     * Nothing if the hut, what its crafters' other tasks reserve apart, already holds the ingredients for all the
     * task's runs; else one request per ingredient of {@code module}'s first recipe for the output; empty if no recipe
     * of the module makes it any more.
     */
    static Optional<List<Requestable>> of(Colony colony, Building hut, CraftingModule module, Crafting task) {
        Predicate<ItemKey> output = task.stack()::equals;
        FulfillQuery query = new FulfillQuery(task.count(), RecipeReservations.reserved(colony, hut, module));
        if (RecipeChoice.firstFulfillable(colony, hut, module, output, query).isPresent()) {
            return Optional.of(List.of());
        }
        RecipeCatalog catalog = colony.context().ports().crafting().catalog();
        return RecipeChoice.firstRecipe(colony, hut, module, output)
                .map(Chosen::recipe)
                .map(recipe -> requests(recipe, task, catalog));
    }

    /**
     * MC createRequestsForRecipe: each ingredient for all the runs, at least for the minimum runs; an ingredient a run
     * gives back (a bucket) once.
     */
    private static List<Requestable> requests(Recipe recipe, Crafting task, RecipeCatalog catalog) {
        List<Requestable> out = new ArrayList<>();
        for (Ingredient in : recipe.cleanedInput()) {
            if (RecipeMatching.givenBack(recipe, in, catalog)) {
                out.add(IngredientRequests.of(in, in.amount(), in.amount(), catalog));
            } else {
                out.add(IngredientRequests.of(in, in.amount() * task.count(), in.amount() * task.minCount(), catalog));
            }
        }
        return out;
    }
}
