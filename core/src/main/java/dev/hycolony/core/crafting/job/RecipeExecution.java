package dev.hycolony.core.crafting.job;

import dev.hycolony.core.building.Building;
import dev.hycolony.core.citizen.CitizenData;
import dev.hycolony.core.colony.Colony;
import dev.hycolony.core.colony.ConstructionPorts;
import dev.hycolony.core.crafting.module.AssignedCitizens;
import dev.hycolony.core.crafting.recipe.Ingredient;
import dev.hycolony.core.crafting.recipe.Recipe;
import dev.hycolony.core.crafting.recipe.RecipeCatalog;
import dev.hycolony.core.crafting.recipe.RecipeMatching;
import dev.hycolony.core.kernel.item.Inventory;
import dev.hycolony.core.kernel.item.ItemAmount;
import dev.hycolony.core.kernel.item.ItemKey;
import dev.hycolony.core.kernel.port.ItemCatalog;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Makes one run of a recipe with real items (MC RecipeStorage.fullfillRecipeAndCopy): takes its ingredients, then puts
 * its primary and secondary outputs in their place. A required tool is not an ingredient here: the crafter wears it
 * down separately.
 */
public final class RecipeExecution {
    private static final System.Logger LOG = System.getLogger(RecipeExecution.class.getName());

    private RecipeExecution() {}

    /**
     * MC AbstractCraftingBuildingModule.fullFillRecipe: one run with the hut's racks, then its workers' inventories (MC
     * getHandlers, a hash set whose order MC leaves open: the racks come first, so the output lands where the hut's
     * requests find it). False, nothing changed, if an ingredient is missing, free slots lack for the extra outputs
     * (MC checkForFreeSpace) or the hut has no worker (MC's handler list is then empty). As in MC, an output that finds
     * no room after all is lost (logged).
     */
    public static boolean craftInHut(Colony colony, Building hut, Recipe recipe) {
        List<CitizenData> workers = AssignedCitizens.of(colony, hut);
        if (workers.isEmpty()) {
            return false;
        }
        ConstructionPorts ports = colony.context().ports();
        List<RunStock> stocks = new ArrayList<>(workers.size() + 1);
        stocks.add(new RunStock.RackStock(hut.containers(), ports.containers()));
        for (CitizenData worker : workers) {
            stocks.add(new RunStock.InventoryStock(worker.inventory(), ports.catalog()::maxStack));
        }
        RecipeCatalog catalog = ports.crafting().catalog();
        if (!hasRoom(recipe, stocks) || !holdsInputs(recipe, stocks, catalog)) {
            return false;
        }
        takeInputs(recipe, stocks, catalog); // cannot fall short: holdsInputs just counted the same stocks
        for (ItemAmount lost : putOutputs(recipe, stocks)) {
            LOG.log(
                    System.Logger.Level.DEBUG,
                    "Hut {0}: {1} x {2} crafted but lost, no room",
                    hut.displayName(),
                    lost.count(),
                    lost.item().id());
        }
        return true;
    }

    /**
     * MC RecipeStorage.fullfillRecipeAndCopy with the crafter's inventory (AbstractEntityAICrafting
     * .executeCraftingAction): takes one run's ingredients, adds its outputs and returns them, primary first; empty,
     * nothing changed, if an ingredient is missing or an output does not fit. Deviation from MC: room is checked
     * exactly, on a copy of the inventory; MC estimates it from the free slots, and may then take the ingredients of
     * an output that finds no slot, which is lost.
     */
    public static Optional<List<ItemAmount>> craftOnce(
            Recipe recipe, Inventory inventory, RecipeCatalog recipes, ItemCatalog items) {
        if (!runOn(recipe, new RunStock.InventoryStock(inventory.copy(), items::maxStack), recipes)) {
            return Optional.empty();
        }
        runOn(recipe, new RunStock.InventoryStock(inventory, items::maxStack), recipes); // the copy just succeeded
        return Optional.of(outputs(recipe));
    }

    /** One run on {@code stock} alone: false if an ingredient ran short or an output did not fit, the stock changed. */
    private static boolean runOn(Recipe recipe, RunStock stock, RecipeCatalog catalog) {
        List<RunStock> stocks = List.of(stock);
        return takeInputs(recipe, stocks, catalog) && putOutputs(recipe, stocks).isEmpty();
    }

    /** What one run gives: its primary output, then its secondary outputs. */
    private static List<ItemAmount> outputs(Recipe recipe) {
        List<ItemAmount> out = new ArrayList<>(recipe.secondaryOutputs().size() + 1);
        out.add(recipe.primaryOutput());
        out.addAll(recipe.secondaryOutputs());
        return out;
    }

    /**
     * MC checkForFreeSpace: a run giving more stacks than the recipe has inputs needs a free slot for each extra one.
     * MC counts the inputs' containers (a bucket) when there is no secondary output; a Hytale recipe lists them as
     * secondary outputs.
     */
    private static boolean hasRoom(Recipe recipe, List<RunStock> stocks) {
        int extra = outputs(recipe).size() - recipe.inputs().size();
        if (extra <= 0) {
            return true;
        }
        long free = 0;
        for (RunStock stock : stocks) {
            free += stock.freeSlots();
        }
        return free >= extra;
    }

    /** MC canFullFillRecipe(1, none, handlers): the stocks together hold each ingredient of one run. */
    private static boolean holdsInputs(Recipe recipe, List<RunStock> stocks, RecipeCatalog catalog) {
        for (Ingredient in : recipe.cleanedInput()) {
            int available = 0;
            for (ItemKey item : RecipeMatching.items(in, catalog)) {
                for (RunStock stock : stocks) {
                    available += stock.count(item);
                }
            }
            if (available < in.amount()) {
                return false;
            }
        }
        return true;
    }

    /**
     * Takes each ingredient of one run from the stocks in order, any item it accepts; false if one ran short, what
     * was taken staying taken.
     */
    private static boolean takeInputs(Recipe recipe, List<RunStock> stocks, RecipeCatalog catalog) {
        for (Ingredient in : recipe.cleanedInput()) {
            int needed = in.amount();
            for (RunStock stock : stocks) {
                for (ItemKey item : RecipeMatching.items(in, catalog)) {
                    needed -= needed > 0 ? stock.extract(item, needed) : 0;
                }
            }
            if (needed > 0) {
                return false;
            }
        }
        return true;
    }

    /** MC insertCraftedItems: each output goes to the first stocks with room; returns what fit nowhere. */
    private static List<ItemAmount> putOutputs(Recipe recipe, List<RunStock> stocks) {
        List<ItemAmount> left = new ArrayList<>();
        for (ItemAmount output : outputs(recipe)) {
            ItemAmount rest = output;
            for (int i = 0; i < stocks.size() && rest != null; i++) {
                rest = stocks.get(i).insert(rest);
            }
            if (rest != null) {
                left.add(rest);
            }
        }
        return left;
    }
}
