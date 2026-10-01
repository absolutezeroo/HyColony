package dev.hycolony.core.crafting.restaurant;

import dev.hycolony.core.building.module.ModuleTab;
import dev.hycolony.core.kernel.item.ItemKey;
import java.util.List;

/**
 * A dining hall's Menu tab (MC RestaurantMenuModuleWindow): the menu and the foods that may join it, sorted as MC
 * (tier first, then nutrition), the ingredients the menu takes with how many the customers eat a day, and MC's two
 * warnings. {@code full}: the menu's limit is reached (MC hasReachedLimit); {@code poor}: no dish of tier 2 or more.
 */
public record MenuView(List<Dish> menu, List<Dish> options, List<Ingredient> ingredients, boolean full, boolean poor)
        implements ModuleTab {
    /** Keeps its own copies of the lists. */
    public MenuView {
        menu = List.copyOf(menu);
        options = List.copyOf(options);
        ingredients = List.copyOf(ingredients);
    }

    /**
     * A food: its tier (the row's colour) and the home level it feeds up to (MC FOOD_QUALITY_TOOLTIP); {@code onMenu}
     * greys its {@code <<} button out among the options.
     */
    public record Dish(ItemKey item, int tier, int homeLevel, boolean onMenu) {}

    /**
     * An ingredient of the menu's recipes, {@code amount} a dish, and what the customers eat of it a day (MC
     * FOOD_CONSUMPTION_TOOLTIP, from {@code dailyMin} to {@code dailyMax}, decimals that MC shows as they are).
     */
    public record Ingredient(ItemKey item, int amount, double dailyMin, double dailyMax) {}
}
