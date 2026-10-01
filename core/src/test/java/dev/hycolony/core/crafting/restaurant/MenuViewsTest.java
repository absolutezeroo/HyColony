package dev.hycolony.core.crafting.restaurant;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.hycolony.core.building.Building;
import dev.hycolony.core.citizen.CitizenData;
import dev.hycolony.core.construction.hut.ConstructionBuildingTypes;
import dev.hycolony.core.kernel.BlockPos;
import dev.hycolony.core.kernel.item.ItemKey;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/** MC RestaurantMenuModuleWindow: the menu and options sorted as MC, its warnings and its ingredients. */
class MenuViewsTest extends DiningHallFixture {
    private MenuView view() {
        return (MenuView) menu().tab(colony, hall, UUID.randomUUID());
    }

    private static List<ItemKey> items(List<MenuView.Dish> dishes) {
        return dishes.stream().map(MenuView.Dish::item).toList();
    }

    @Test
    void anEmptyMenuWarnsAndOffersEveryEdibleFoodBestFirst() {
        MenuView v = view();
        assertTrue(v.menu().isEmpty());
        assertTrue(v.poor());
        assertEquals(List.of(pie, steak), items(v.options())); // tier x -100 - nutrition; raw meat is no option
    }

    @Test
    void aQualityDishEndsThePoorWarningAndALevelHidesTheLeanestFoods() {
        menu().add(colony, hall, pie);
        assertFalse(view().poor());
        assertTrue(view().options().stream()
                .filter(d -> d.item().equals(pie))
                .findFirst()
                .orElseThrow()
                .onMenu());
        hall.setLevel(5);
        ItemKey berry = t.catalog.food("berry", 2, 0);
        assertFalse(items(view().options()).contains(berry)); // nutrition >= level - 1
    }

    @Test
    void theIngredientsComeFromTheDishesRecipesWithTheCustomersDailyNeed() {
        menu().add(colony, hall, steak); // cooks from meat, one for one
        Building home = Building.create(ConstructionBuildingTypes.RESIDENCE, new BlockPos(30, 64, 0), 0);
        home.setLevel(2);
        colony.buildings().add(home);
        for (int id = 1; id <= 2; id++) {
            CitizenData c = new CitizenData(id);
            c.setHomeBuilding(home.position());
            colony.citizens().restore(c);
            menu().storeCustomer(colony, hall, id);
        }
        menu().storeCustomer(colony, hall, 3); // a customer the colony no longer knows: MC skips it

        MenuView.Ingredient meat = view().ingredients().getFirst();

        assertEquals(MEAT, meat.item());
        assertEquals(1, meat.amount());
        // MC: 0.725 (home 2) x 2 / 3 customers x 10 x 3 / 8 = 1.8125, cast to 1 before x amount, shown as a double
        assertEquals(1.0, meat.dailyMin());
        assertEquals(1.5, meat.dailyMax());
    }
}
