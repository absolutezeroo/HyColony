package dev.hycolony.core.crafting.module;

import dev.hycolony.core.building.Building;
import dev.hycolony.core.colony.Colony;
import dev.hycolony.core.crafting.recipe.Ingredient;
import dev.hycolony.core.crafting.recipe.Recipe;
import dev.hycolony.core.crafting.recipe.RecipeCatalog;
import dev.hycolony.core.crafting.recipe.RecipeId;
import dev.hycolony.core.crafting.recipe.RecipeMatching;
import dev.hycolony.core.crafting.task.Crafter;
import dev.hycolony.core.crafting.task.Crafters;
import dev.hycolony.core.kernel.item.ItemKey;
import dev.hycolony.core.logistics.pickup.KeepRule;
import dev.hycolony.core.request.Request;
import dev.hycolony.core.request.model.Crafting;
import dev.hycolony.core.request.model.RequestToken;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * What a hut holds back for the crafting tasks its crafters are waiting on or about to make (MC
 * AbstractCraftingBuildingModule reservedStacksExcluding and getRequiredItemsAndAmount, over
 * getPendingRequestQueueExcluding).
 *
 * <p>The building resolver ({@code building.BuildingResolver}), which cannot see the crafting modules, gets
 * {@link #reservedFor} through the colony.
 */
public final class RecipeReservations {
    private RecipeReservations() {}

    /** A task of the hut's crafters: its recipe and how many runs of it. */
    private record Pending(RequestToken token, Recipe recipe, int runs) {}

    /**
     * MC reservedStacks: each ingredient of the pending tasks times their runs, summed by ingredient at amount 1 (MC's
     * {@code ItemStorage} keys ignore the amount), as {@link RecipeChoice.FulfillQuery} takes them.
     */
    public static Map<Ingredient, Integer> reserved(Colony colony, Building hut, CraftingModule module) {
        return reserved(pending(colony, hut, module));
    }

    /**
     * MC AbstractBuilding.reservedStacksExcluding for the building resolver: how many of {@code item} the pending tasks
     * of {@code hut}'s crafting modules hold back, but the tasks {@code excluded} descends from (MC anyChildRequestIs:
     * a task's own ingredients are not held back from it).
     */
    public static int reservedFor(Colony colony, Building hut, Request excluded, ItemKey item) {
        Set<RequestToken> ancestors = new HashSet<>();
        for (Optional<RequestToken> p = excluded.parent(); p.isPresent(); ) {
            ancestors.add(p.get());
            p = colony.requests().get(p.get()).flatMap(Request::parent);
        }
        RecipeCatalog catalog = colony.context().ports().crafting().catalog();
        int total = 0;
        for (var module : hut.modules().values()) {
            if (module instanceof CraftingModule crafting) {
                List<Pending> tasks = pending(colony, hut, crafting).stream()
                        .filter(t -> !ancestors.contains(t.token()))
                        .toList();
                for (Map.Entry<Ingredient, Integer> e : reserved(tasks).entrySet()) {
                    if (RecipeMatching.accepts(e.getKey(), item, catalog)) {
                        total += e.getValue();
                    }
                }
            }
        }
        return total;
    }

    private static Map<Ingredient, Integer> reserved(List<Pending> tasks) {
        Map<Ingredient, Integer> out = new LinkedHashMap<>();
        for (Pending task : tasks) {
            for (Ingredient in : task.recipe().cleanedInput()) {
                out.merge(in.withAmount(1), in.amount() * task.runs(), Integer::sum);
            }
        }
        return out;
    }

    /**
     * MC getRequiredItemsAndAmount: rules keeping the {@link #reserved} ingredients and each pending task's primary
     * output times its runs in the hut's racks, not in a worker's inventory (MC's {@code false} flag).
     */
    static List<KeepRule> keepRules(Colony colony, Building hut, CraftingModule module) {
        List<Pending> tasks = pending(colony, hut, module);
        Map<Ingredient, Integer> kept = reserved(tasks);
        for (Pending task : tasks) {
            Ingredient output =
                    new Ingredient.OfItem(task.recipe().primaryOutput().item(), 1);
            kept.merge(output, task.recipe().primaryOutput().count() * task.runs(), Integer::sum);
        }
        RecipeCatalog catalog = colony.context().ports().crafting().catalog();
        List<KeepRule> out = new ArrayList<>(kept.size());
        kept.forEach((in, amount) ->
                out.add(new KeepRule(item -> RecipeMatching.accepts(in, item, catalog), amount, false)));
        return out;
    }

    /**
     * MC getPendingRequestQueueExcluding(null): the scheduled then queued tasks of every crafter of the hut whose
     * recipe the module holds and the registry knows; a task whose request is gone is skipped.
     */
    private static List<Pending> pending(Colony colony, Building hut, CraftingModule module) {
        List<Pending> out = new ArrayList<>();
        for (Crafter crafter : Crafters.ofHut(colony, hut)) {
            List<RequestToken> tokens = new ArrayList<>(crafter.craftingTasks().assignedTasks());
            tokens.addAll(crafter.craftingTasks().taskQueue());
            for (RequestToken token : tokens) {
                task(colony, module, token).ifPresent(out::add);
            }
        }
        return out;
    }

    /** The task {@code token} names, if it is still a crafting task of a recipe the module holds. */
    private static Optional<Pending> task(Colony colony, CraftingModule module, RequestToken token) {
        if (colony.requests().get(token).map(Request::requestable).orElse(null) instanceof Crafting task) {
            RecipeId id = new RecipeId(task.recipeId());
            if (module.holdsRecipe(id)) {
                return colony.registries().recipes().get(id).map(recipe -> new Pending(token, recipe, task.count()));
            }
        }
        return Optional.empty();
    }
}
