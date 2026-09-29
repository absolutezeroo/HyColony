package dev.hycolony.core.crafting.module;

import dev.hycolony.core.building.module.ModuleTab;
import dev.hycolony.core.kernel.item.ItemAmount;
import dev.hycolony.core.kernel.item.ToolType;
import java.util.List;
import java.util.Optional;

/**
 * A crafting hut's Recipes tab (MC CraftingModuleView, drawn by WindowListRecipes): {@code active} of {@code max}
 * recipes (MC RECIPE_STATUS), the learnt recipes in the order the crafters try them, then those the hut could learn,
 * by output id. A learnt line's position is the index its toggle and move buttons send (MC's row index).
 *
 * <p>Deviation from MC: a recipe is learnt by choosing it in {@code learnable}, the recipes of the game the hut can
 * hold, where MC lays it out in a crafting grid.
 */
public record RecipesView(int active, int max, List<Line> learned, List<Line> learnable) implements ModuleTab {
    public RecipesView {
        learned = List.copyOf(learned);
        learnable = List.copyOf(learnable);
    }

    /**
     * One recipe: its id in the colony registry (the buttons' argument), what one run makes and takes (cleaned
     * input), its bench (empty: made by hand) and required tool. {@code disabled} and {@code custom} (granted by the
     * hut level, without Remove button in MC) mark learnt lines. {@code refusal} is the lang key of why the line's
     * Learn button, or a disabled line's Enable button (hidden by MC while the hut is full), is refused.
     */
    public record Line(
            String recipeId,
            ItemAmount output,
            List<IngredientLine> inputs,
            Optional<String> bench,
            Optional<ToolType> tool,
            boolean disabled,
            boolean custom,
            Optional<String> refusal) {
        public Line {
            inputs = List.copyOf(inputs);
        }

        /** MC: a learnt recipe has a Remove button unless the hut level granted it. */
        public boolean removable() {
            return !custom;
        }
    }

    /**
     * One ingredient. For a Hytale resource type or tag ({@code orEquivalent}: any of its items will do), {@code shown}
     * is the first item the game lists for it, or, if none, its id. Deviation from MC: that item stands for the type
     * or tag, as for a StackList request; MC recipes name an exact item.
     */
    public record IngredientLine(ItemAmount shown, boolean orEquivalent) {}
}
