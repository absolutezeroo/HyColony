package dev.hycolony.core.crafting.request;

import dev.hycolony.core.crafting.module.RecipeChoice.Chosen;
import dev.hycolony.core.crafting.recipe.Ingredient;
import dev.hycolony.core.crafting.recipe.Recipe;
import dev.hycolony.core.crafting.recipe.RecipeCatalog;
import dev.hycolony.core.crafting.recipe.RecipeMatching;
import dev.hycolony.core.kernel.catalog.ItemCatalog;
import dev.hycolony.core.kernel.item.ItemAmount;
import dev.hycolony.core.kernel.item.ItemKey;
import dev.hycolony.core.request.model.Crafting;
import java.util.ArrayList;
import java.util.List;

/**
 * MC AbstractCraftingRequestResolver.createRequestsForRecipe: splits a request's runs of a recipe into crafting tasks
 * whose output and ingredients all fit a crafter's inventory, one slot of each row of 8 kept free.
 */
final class CraftingBatches {
    /** MC: a citizen's inventory slots; the CITIZEN_INV_SLOTS research that adds some is not ported (0). */
    static final int INVENTORY_SLOTS = 27;
    /** MC: one slot per row of this many is kept for the overhead. */
    static final int ROW_SLOTS = 8;
    /** The stack size of an ingredient no item answers (MC's usual ItemStack.getMaxStackSize). */
    static final int DEFAULT_MAX_STACK = 64;

    private final ItemCatalog items;
    private final RecipeCatalog recipes;

    CraftingBatches(ItemCatalog items, RecipeCatalog recipes) {
        this.items = items;
        this.recipes = recipes;
    }

    /**
     * One {@link Crafting} per batch for {@code count} items, at least {@code minCount}, both turned into runs of the
     * recipe (rounded up): {@code min(batch, runs left)} runs, at least {@code max(1, min(batch, minimum runs left))}.
     */
    List<Crafting> split(Chosen chosen, int count, int minCount, boolean isPublic) {
        ItemAmount output = chosen.recipe().primaryOutput();
        int runs = (int) Math.ceil((double) count / output.count());
        int minRuns = (int) Math.ceil((double) minCount / output.count());
        int batch = batchSize(chosen.recipe(), runs);
        List<Crafting> tasks = new ArrayList<>();
        while (runs > 0) {
            tasks.add(new Crafting(
                    output.item(),
                    Math.min(batch, runs),
                    Math.max(1, Math.min(batch, minRuns)),
                    chosen.id().value(),
                    isPublic));
            runs -= batch;
            minRuns = minRuns > batch ? minRuns - batch : 0;
        }
        return tasks;
    }

    /**
     * MC's batch size: all {@code runs} at first, then scaled down by the ratio of free slots to needed slots until
     * they fit. Deviation from MC: a single run that does not fit makes batches of one run; MC's batch size drops to
     * 0 and it asks for tasks forever.
     */
    private int batchSize(Recipe recipe, int runs) {
        int maxSlots = INVENTORY_SLOTS - INVENTORY_SLOTS % ROW_SLOTS;
        int batch = runs;
        int totalSlots = Integer.MAX_VALUE;
        while (totalSlots > maxSlots) {
            int stacks = stacksNeeded(recipe, batch);
            if (stacks > maxSlots) {
                if (batch <= 1) {
                    return 1;
                }
                batch = Math.max(1, (int) Math.floor((double) batch * ((double) maxSlots / stacks)));
            }
            totalSlots = Math.min(totalSlots, stacks);
        }
        return batch;
    }

    /**
     * The stacks {@code batch} runs hold at once: the whole output and every ingredient, as MC naively counts; an
     * ingredient the recipe gives back takes one slot.
     */
    private int stacksNeeded(Recipe recipe, int batch) {
        ItemAmount output = recipe.primaryOutput();
        int stacks = (int) Math.ceil((double) (output.count() * batch) / maxStack(output.item()));
        for (Ingredient in : recipe.cleanedInput()) {
            if (RecipeMatching.givenBack(recipe, in, recipes)) {
                stacks += 1;
            } else {
                stacks += (int) Math.ceil((double) (in.amount() * batch) / maxStackOf(in));
            }
        }
        return stacks;
    }

    /** The stack size of the ingredient's first item (a type or tag stands for its items), else the default. */
    private int maxStackOf(Ingredient in) {
        List<ItemKey> answering = RecipeMatching.items(in, recipes);
        return answering.isEmpty() ? DEFAULT_MAX_STACK : maxStack(answering.getFirst());
    }

    /** Never 0: a port answering 0 must not divide by zero. */
    private int maxStack(ItemKey item) {
        return Math.max(1, items.maxStack(item));
    }
}
