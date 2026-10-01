package dev.hycolony.core.citizen.food;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.hycolony.core.kernel.item.FoodInfo;
import dev.hycolony.core.kernel.item.ItemKey;
import dev.hycolony.core.testing.FakeCatalog;
import org.junit.jupiter.api.Test;

/** MC FoodUtils on HyColony's food table. */
class FoodRulesTest {
    private final FakeCatalog catalog = new FakeCatalog();
    private final ItemKey apple = catalog.food("apple", 4, 0);
    private final ItemKey meat = catalog.food("meat", 3, 0);
    private final ItemKey steak = catalog.food("steak", 8, 0);
    private final ItemKey pie = catalog.food("pie", 12, 3);
    private final ItemKey stone = new ItemKey("stone");

    FoodRulesTest() {
        catalog.cooked.put(meat, steak);
    }

    @Test
    void rawFoodThatCooksIsNotEdible() {
        assertTrue(FoodRules.edible(catalog, apple));
        assertFalse(FoodRules.edible(catalog, meat));
        assertFalse(FoodRules.edible(catalog, stone));
    }

    @Test
    void anyFoodFeedsALowHomeAndBetterHomesWantMoreNutrition() {
        assertTrue(FoodRules.canEatLevel(catalog, apple, 0));
        assertTrue(FoodRules.canEatLevel(catalog, apple, 2));
        assertTrue(FoodRules.canEatLevel(catalog, apple, 3));
        assertFalse(FoodRules.canEatLevel(catalog, apple, 4));
        assertTrue(FoodRules.canEatLevel(catalog, steak, 5));
        assertFalse(FoodRules.canEatLevel(catalog, stone, 0));
    }

    @Test
    void poisonousFoodAndFoodTheWorkHutKeepsAreNotEaten() {
        ItemKey shroom = new ItemKey("shroom");
        catalog.foods.put(shroom, new FoodInfo(1, 0, true));
        assertFalse(FoodRules.canEat(catalog, shroom, 0, _ -> true));
        assertTrue(FoodRules.canEat(catalog, apple, 0, _ -> true));
        assertFalse(FoodRules.canEat(catalog, apple, 0, i -> !i.equals(apple)));
        assertFalse(FoodRules.canEat(catalog, meat, 0, _ -> true));
    }

    @Test
    void aDishCountsTwiceAndTiersComeFromTheTable() {
        assertEquals(4, FoodRules.foodValue(catalog, apple));
        assertEquals(24, FoodRules.foodValue(catalog, pie));
        assertEquals(0, FoodRules.foodValue(catalog, stone));
        assertEquals(3, FoodRules.tier(catalog, pie));
        assertTrue(FoodRules.isDish(catalog, pie));
        assertFalse(FoodRules.isDish(catalog, apple));
    }

    @Test
    void buildingLevelForFoodIsNutritionMinusOneBetweenTwoAndFive() {
        assertEquals(3, FoodRules.buildingLevelForFood(catalog, apple));
        assertEquals(5, FoodRules.buildingLevelForFood(catalog, pie));
        assertEquals(2, FoodRules.buildingLevelForFood(catalog, catalog.food("egg", 2, 0)));
    }

    @Test
    void requirementsAndConsumptionFollowTheHomeLevel() {
        assertEquals(0, FoodRules.minQuality(2));
        assertEquals(3, FoodRules.minQuality(5));
        assertEquals(4, FoodRules.minDiversity(4));
        assertEquals(0.3, FoodRules.consumptionFactor(0));
        assertEquals(0.725, FoodRules.consumptionFactor(2));
        assertEquals(1.5, FoodRules.consumptionFactor(5));
    }
}
