package dev.hycolony.core.crafting.module;

import dev.hycolony.core.building.Building;
import dev.hycolony.core.citizen.CitizenData;
import dev.hycolony.core.colony.Colony;
import dev.hycolony.core.crafting.recipe.Ingredient;
import dev.hycolony.core.crafting.recipe.Recipe;
import dev.hycolony.core.crafting.recipe.RecipeCatalog;
import dev.hycolony.core.crafting.recipe.RecipeId;
import dev.hycolony.core.crafting.recipe.RecipeMatching;
import dev.hycolony.core.kernel.item.ItemKey;
import dev.hycolony.core.kernel.port.ContainerAccess;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Predicate;

/**
 * Which learnt recipe a hut uses for an item (MC AbstractCraftingBuildingModule.getFirstRecipe and
 * getFirstFulfillableRecipe): the first active one of its list, in order, whose primary output answers. MC also
 * matches the alternate outputs of its multi-output recipes, one of which a run makes; Hytale has no such recipe, and a
 * secondary output (a bucket given back) is never matched, as in MC. Deviation from MC: research is not ported, so the
 * {@code RECIPE_MODE} setting it unlocks stays {@code PRIORITY}: the list order always decides, never the warehouse
 * stock ({@code MAX_STOCK}).
 */
public final class RecipeChoice {
    private RecipeChoice() {}

    /** A learnt recipe and its id in the colony registry. */
    public record Chosen(RecipeId id, Recipe recipe) {}

    /**
     * How many runs must be possible, and the amounts other tasks of the hut already hold, by ingredient at amount 1
     * (MC's {@code ItemStorage} keys ignore the amount), which are not available.
     */
    public record FulfillQuery(int count, Map<Ingredient, Integer> reserved) {
        public FulfillQuery {
            reserved = Map.copyOf(reserved);
        }

        /** {@code count} runs, nothing reserved (MC {@code considerReservation = false}). */
        public static FulfillQuery of(int count) {
            return new FulfillQuery(count, Map.of());
        }
    }

    /**
     * MC getFirstRecipe: the first active recipe of the list making an item {@code output} accepts; empty if none.
     * Deviation from MC: a recipe that is no longer valid (its bench broken, see {@link
     * RecipeCompatibility#stillValid}) is skipped.
     */
    public static Optional<Chosen> firstRecipe(
            Colony colony, Building hut, CraftingModule module, Predicate<ItemKey> output) {
        for (RecipeId id : module.recipes()) {
            Optional<Chosen> chosen = candidate(colony, hut, module, id, output);
            if (chosen.isPresent()) {
                return chosen;
            }
        }
        return Optional.empty();
    }

    /**
     * MC getFirstFulfillableRecipe: the first recipe {@link #firstRecipe} would consider whose ingredients for
     * {@code query.count()} runs are in the hut or its workers' inventories; empty if none.
     */
    public static Optional<Chosen> firstFulfillable(
            Colony colony, Building hut, CraftingModule module, Predicate<ItemKey> output, FulfillQuery query) {
        for (RecipeId id : module.recipes()) {
            Optional<Chosen> chosen =
                    candidate(colony, hut, module, id, output).filter(c -> canFullFill(colony, hut, c.recipe(), query));
            if (chosen.isPresent()) {
                return chosen;
            }
        }
        return Optional.empty();
    }

    /**
     * MC RecipeStorage.canFullFillRecipe: whether the hut's containers and its workers' inventories hold each
     * ingredient for {@code query.count()} runs, on top of what is reserved. An ingredient the recipe gives back as a
     * secondary output is needed once, whatever the count.
     */
    public static boolean canFullFill(Colony colony, Building hut, Recipe recipe, FulfillQuery query) {
        RecipeCatalog catalog = colony.context().ports().crafting().catalog();
        List<CitizenData> workers = AssignedCitizens.of(colony, hut);
        ContainerAccess containers = colony.context().ports().containers();
        for (Ingredient in : recipe.cleanedInput()) {
            int available = 0;
            for (ItemKey item : RecipeMatching.items(in, catalog)) {
                available += containers.count(hut.containers(), item);
                for (CitizenData worker : workers) {
                    available += worker.inventory().count(item);
                }
            }
            int needed = RecipeMatching.givenBack(recipe, in, catalog) ? in.amount() : in.amount() * query.count();
            if (available < needed + query.reserved().getOrDefault(in.withAmount(1), 0)) {
                return false;
            }
        }
        return true;
    }

    /** The listed recipe {@code id}, if active, still valid and making an item {@code output} accepts. */
    private static Optional<Chosen> candidate(
            Colony colony, Building hut, CraftingModule module, RecipeId id, Predicate<ItemKey> output) {
        if (module.isDisabled(id)) {
            return Optional.empty();
        }
        return colony.registries()
                .recipes()
                .get(id)
                .filter(r -> output.test(r.primaryOutput().item()))
                .filter(r -> RecipeCompatibility.stillValid(colony, hut, module, id))
                .map(r -> new Chosen(id, r));
    }
}
