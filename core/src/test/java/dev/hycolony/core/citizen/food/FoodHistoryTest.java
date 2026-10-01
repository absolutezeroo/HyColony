package dev.hycolony.core.citizen.food;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.hycolony.core.kernel.item.ItemKey;
import dev.hycolony.core.testing.FakeCatalog;
import java.util.Optional;
import org.junit.jupiter.api.Test;

/** MC CitizenFoodHandler: the last ten meals. */
class FoodHistoryTest {
    private final FakeCatalog catalog = new FakeCatalog();
    private final ItemKey apple = catalog.food("apple", 4, 0);
    private final ItemKey pie = catalog.food("pie", 12, 3);
    private final FoodHistory history = new FoodHistory();

    @Test
    void emptyHistoryHasDiversityOneAndNoLastMeal() {
        assertEquals(new FoodHistory.Stats(1, 0), history.stats(catalog));
        assertEquals(Optional.empty(), history.last());
        assertEquals(-1, history.lastIndexOf(apple));
        assertFalse(history.isFull());
    }

    @Test
    void keepsTheLastTenMealsAndForgetsTheOldest() {
        history.add(pie);
        for (int i = 0; i < 10; i++) {
            history.add(apple);
        }
        assertTrue(history.isFull());
        assertEquals(10, history.foods().size());
        assertEquals(-1, history.lastIndexOf(pie));
        assertEquals(9, history.lastIndexOf(apple));
    }

    @Test
    void statsCountDistinctFoodsAndDishes() {
        history.add(apple);
        history.add(pie);
        history.add(pie);
        assertEquals(new FoodHistory.Stats(2, 2), history.stats(catalog));
        assertEquals(Optional.of(pie), history.last());
        assertEquals(2, history.lastIndexOf(pie));
        assertEquals(0, history.lastIndexOf(apple));
    }
}
