package dev.hycolony.plugin.ui.hut;

import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.ui.builder.UICommandBuilder;
import dev.hycolony.core.crafting.module.RecipesView;
import dev.hycolony.core.kernel.item.ItemAmount;
import java.util.List;
import java.util.Locale;

/**
 * Fills a recipe line (Mc/RecipeLine.ui) as MC WindowListRecipes.updateElement: the ingredients in the 3 x 3 grid (a
 * four-ingredient recipe as a 2 x 2 square), the output with its count, the required tool, and the grey of a disabled
 * recipe. The buttons are the caller's.
 */
public final class RecipeLines {
    /** The document of one line. */
    public static final String DOCUMENT = "Pages/HyColony/Mc/RecipeLine.ui";

    /** MC's 2 x 2 layout of a four-ingredient recipe: res1, res2, res4, res5. */
    private static final int[] SQUARE = {0, 1, 3, 4};

    private static final int GRID = 9;

    private RecipeLines() {}

    /** Fills the line at {@code sel}. */
    public static void fill(UICommandBuilder ui, String sel, RecipesView.Line line) {
        List<RecipesView.IngredientLine> inputs = line.inputs();
        for (int i = 0; i < Math.min(GRID, inputs.size()); i++) {
            int cell = inputs.size() == SQUARE.length ? SQUARE[i] : i;
            stack(ui, sel + " #In" + cell, inputs.get(i).shown());
            ui.set(sel + " #In" + cell + ".Visible", true);
        }
        stack(ui, sel + " #Output", line.output());
        // MC shows the required tool; it sets the intermediate block's name too but leaves it hidden.
        line.tool()
                .ifPresent(t -> ui.set(
                        sel + " #Intermediate.Text",
                        Message.translation("hycolony.ui.tool." + t.name().toLowerCase(Locale.ROOT))));
        ui.set(sel + " #Grey.Visible", line.disabled());
    }

    /** An item icon with MC's stack count drawn over it (none for a single item). */
    private static void stack(UICommandBuilder ui, String cell, ItemAmount a) {
        ui.set(cell + " #Icon.ItemId", a.item().id());
        ui.set(cell + " #Count.Text", a.count() > 1 ? String.valueOf(a.count()) : "");
    }
}
