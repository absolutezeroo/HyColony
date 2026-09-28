package dev.hycolony.core.crafting.job;

import dev.hycolony.core.crafting.recipe.Ingredient;
import dev.hycolony.core.crafting.recipe.Recipe;
import dev.hycolony.core.crafting.recipe.RecipeCatalog;
import dev.hycolony.core.crafting.recipe.RecipeMatching;
import dev.hycolony.core.job.work.WorkerStock;
import dev.hycolony.core.kernel.item.ItemAmount;
import dev.hycolony.core.kernel.item.ItemKey;
import dev.hycolony.core.kernel.port.ItemCatalog;
import dev.hycolony.core.request.model.Crafting;
import java.util.Optional;

/**
 * The counts behind a crafter's recipe (MC AbstractEntityAICrafting.getRecipe and checkForItems): how many runs it may
 * make, and which ingredient its inventory still lacks. An ingredient the recipe gives back (a bucket) is needed once
 * whatever the runs. MC's extended count (what a furnace holds) is 0 for these crafters.
 */
final class RecipeCounts {
    /** MC WorkerUtil.hasTooManyExternalItemsInInv: past this many slots of other items, the crafter dumps first. */
    static final int MAX_OTHER_SLOTS = 3;

    /** The runs the crafter may make now (MC maxCraftingCount), those already made among them (MC craftCounter). */
    record Counts(int maxCraftingCount, int craftCounter) {}

    /** MC needsCurrently: an ingredient to fetch from the hut, and how many of it the inventory should hold. */
    record Needed(Ingredient ingredient, int amount) {}

    private final WorkerStock stock;
    private final RecipeCatalog recipes;
    private final ItemCatalog items;

    RecipeCounts(WorkerStock stock, RecipeCatalog recipes, ItemCatalog items) {
        this.stock = stock;
        this.recipes = recipes;
        this.items = items;
    }

    /**
     * MC getRecipe's counts for {@code task}: the outputs the crafter carries are runs already made; each ingredient,
     * in the hut and the inventory, bounds the runs. Empty when an ingredient falls short of the runs still due for the
     * task's minimum (MC then fails the task).
     */
    Optional<Counts> forTask(Recipe recipe, Crafting task) {
        ItemAmount output = recipe.primaryOutput();
        int done = stock.inventory().count(output.item()) / output.count();
        int minRemaining = task.minCount() - done;
        int available = task.count();
        for (Ingredient in : recipe.cleanedInput()) {
            boolean givenBack = RecipeMatching.givenBack(recipe, in, recipes);
            int remaining = givenBack ? in.amount() : in.amount() * minRemaining;
            int held = inHut(in) + inInventory(in);
            if (held < remaining) {
                return Optional.empty();
            }
            if (!givenBack) {
                available = Math.min(held / in.amount(), available);
            }
        }
        return Optional.of(new Counts(Math.min(available + done, task.count()), done));
    }

    /**
     * MC checkForItems: the first ingredient the inventory lacks for the {@code maxCraftingCount} runs, the
     * {@code craftCounter} already made counting as held; empty when the crafter holds them all.
     */
    Optional<Needed> shortfall(Recipe recipe, int maxCraftingCount, int craftCounter) {
        for (Ingredient in : recipe.cleanedInput()) {
            int held = inInventory(in);
            int remaining = RecipeMatching.givenBack(recipe, in, recipes)
                    ? in.amount()
                    : in.amount() * Math.max(maxCraftingCount, 1);
            if (held <= 0 || held + craftCounter * in.amount() < remaining) {
                return Optional.of(new Needed(in, remaining));
            }
        }
        return Optional.empty();
    }

    /**
     * MC getNeededItem's transfer (tryTransferFromPosToWorkerIfNeeded): takes from the hut what the inventory lacks of
     * {@code needed}, any item the ingredient accepts; what does not fit stays in the hut.
     */
    void fetch(Needed needed) {
        int missing = needed.amount() - inInventory(needed.ingredient());
        for (ItemKey item : RecipeMatching.items(needed.ingredient(), recipes)) {
            if (missing <= 0) {
                return;
            }
            missing -= stock.take(item, missing);
        }
    }

    /** MC WorkerUtil.hasTooManyExternalItemsInInv: more than {@link #MAX_OTHER_SLOTS} slots hold other items. */
    boolean tooManyOtherItems(Recipe recipe) {
        int others = 0;
        for (ItemAmount stack : stock.inventory().contents()) {
            if (!isPartOf(recipe, stack.item()) && ++others > MAX_OTHER_SLOTS) {
                return true;
            }
        }
        return false;
    }

    /**
     * MC WorkerUtil.isPartOfRecipe: the recipe's output, a secondary output, its tool (MC's crafting tools) or one of
     * its ingredients.
     */
    private boolean isPartOf(Recipe recipe, ItemKey item) {
        if (recipe.primaryOutput().item().equals(item)
                || recipe.secondaryOutputs().stream().anyMatch(out -> out.item().equals(item))) {
            return true;
        }
        if (recipe.requiredTool().isPresent()
                && items.tool(item)
                        .map(t -> t.type() == recipe.requiredTool().get())
                        .orElse(false)) {
            return true;
        }
        return recipe.cleanedInput().stream().anyMatch(in -> RecipeMatching.accepts(in, item, recipes));
    }

    /** How many items {@code in} accepts the inventory holds. */
    int inInventory(Ingredient in) {
        int n = 0;
        for (ItemKey item : RecipeMatching.items(in, recipes)) {
            n += stock.inventory().count(item);
        }
        return n;
    }

    /** How many items {@code in} accepts the hut holds, worn-out tools apart. */
    int inHut(Ingredient in) {
        int n = 0;
        for (ItemKey item : RecipeMatching.items(in, recipes)) {
            n += stock.hutCount(item);
        }
        return n;
    }
}
