package dev.hycolony.core.crafting.restaurant;

import dev.hycolony.core.building.Building;
import dev.hycolony.core.citizen.CitizenData;
import dev.hycolony.core.citizen.food.FoodRules;
import dev.hycolony.core.colony.Colony;
import dev.hycolony.core.kernel.catalog.FoodCatalog;
import dev.hycolony.core.kernel.item.FoodInfo;
import dev.hycolony.core.kernel.item.ItemAmount;
import dev.hycolony.core.kernel.item.ItemKey;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * Builds a dining hall's {@link MenuView} (MC RestaurantMenuModuleWindow: updateStockList, updateResources and the
 * consumption of its constructor). Deviation from MC: dishes of the same tier and nutrition sort by id, where MC sorts
 * by their shown name, which only the client knows.
 */
final class MenuViews {
    /** MC: the tier from which a dish is quality food (the poor-menu warning's test). */
    static final int GOOD_TIER = 2;

    private MenuViews() {}

    /** The menu tab of {@code hall}. */
    static MenuView of(Colony colony, Building hall, RestaurantMenuModule module) {
        FoodCatalog catalog = colony.context().ports().foods();
        Set<ItemKey> menu = module.menu();
        List<MenuView.Dish> dishes = sorted(catalog, menu.stream().toList(), menu);
        List<ItemKey> edibles = catalog.foods().stream()
                .filter(f -> FoodRules.edible(catalog, f))
                .filter(f -> catalog.food(f).map(FoodInfo::nutrition).orElse(0) >= hall.level() - 1)
                .toList();
        boolean poor = menu.stream().noneMatch(f -> FoodRules.tier(catalog, f) >= GOOD_TIER);
        return new MenuView(
                dishes, sorted(catalog, edibles, menu), ingredients(colony, hall, menu), module.full(hall), poor);
    }

    /** MC applySorting: tier x -100 - nutrition, smallest first. */
    private static List<MenuView.Dish> sorted(FoodCatalog catalog, List<ItemKey> foods, Set<ItemKey> menu) {
        Comparator<ItemKey> score =
                Comparator.comparingInt(f -> -100 * FoodRules.tier(catalog, f) - nutrition(catalog, f));
        return foods.stream()
                .sorted(score.thenComparing(ItemKey::id))
                .map(f -> new MenuView.Dish(
                        f, FoodRules.tier(catalog, f), FoodRules.buildingLevelForFood(catalog, f), menu.contains(f)))
                .toList();
    }

    private static int nutrition(FoodCatalog catalog, ItemKey food) {
        return catalog.food(food).map(FoodInfo::nutrition).orElse(0);
    }

    /**
     * MC updateStockList's ingredients: each dish's, a dish's worth (its run's amount over the dishes made), the
     * largest first, with what the customers eat of it a day (MC {@code (int) consumption * amount}, and x 1.5).
     */
    private static List<MenuView.Ingredient> ingredients(Colony colony, Building hall, Set<ItemKey> menu) {
        Map<ItemKey, Double> perDish = new LinkedHashMap<>();
        int saturationSum = 0;
        for (ItemKey dish : menu) {
            MenuIngredients.Result r = MenuIngredients.of(colony, dish);
            for (ItemAmount in : r.ingredients()) {
                perDish.merge(in.item(), (double) in.count() / r.made(), Double::sum);
            }
            saturationSum += (int) FoodRules.foodValue(colony.context().ports().foods(), dish);
        }
        int consumption = (int) consumption(colony, hall, saturationSum);
        List<MenuView.Ingredient> out = new ArrayList<>();
        perDish.entrySet().stream()
                .sorted(Map.Entry.<ItemKey, Double>comparingByValue().reversed())
                .forEach(e -> out.add(new MenuView.Ingredient(
                        e.getKey(),
                        (int) e.getValue().doubleValue(),
                        consumption * e.getValue(),
                        consumption * e.getValue() * 1.5)));
        return out;
    }

    /**
     * MC: the customers' mean saturation factor (from their home's level) x 10 x their number, over the menu's food
     * values; 0 without customers or menu (MC divides by zero).
     */
    private static double consumption(Colony colony, Building hall, int saturationSum) {
        Set<Integer> customers = hall.module(DiningRoomModule.class)
                .map(DiningRoomModule::customers)
                .orElse(Set.of());
        if (customers.isEmpty() || saturationSum == 0) {
            return 0;
        }
        double sum = 0;
        for (int id : customers) {
            // MC skips a customer it no longer knows, yet still divides by all of them.
            Optional<CitizenData> c = colony.citizens().get(id);
            if (c.isPresent()) {
                sum += FoodRules.consumptionFactor(homeLevel(colony, c.get()));
            }
        }
        return sum / customers.size() * 10 * customers.size() / saturationSum;
    }

    private static int homeLevel(Colony colony, CitizenData d) {
        return d.homeBuilding() == null
                ? 0
                : colony.buildings().at(d.homeBuilding()).map(Building::level).orElse(0);
    }
}
