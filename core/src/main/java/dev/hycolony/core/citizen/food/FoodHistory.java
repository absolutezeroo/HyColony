package dev.hycolony.core.citizen.food;

import dev.hycolony.core.kernel.catalog.FoodCatalog;
import dev.hycolony.core.kernel.item.ItemKey;
import java.util.ArrayDeque;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * The last foods a citizen ate, oldest first, at most {@link #SIZE}: their diversity and quality feed its happiness.
 * Port of MC CitizenFoodHandler; its disease modifier is not ported (no disease).
 */
public final class FoodHistory {
    /** MC CitizenFoodHandler.FOOD_QUEUE_SIZE. */
    public static final int SIZE = 10;

    /** MC CitizenFoodStats: distinct foods (at least 1) and dishes among the last meals. */
    public record Stats(int diversity, int quality) {}

    private final ArrayDeque<ItemKey> eaten = new ArrayDeque<>(SIZE);

    /** MC addLastEaten: notes {@code item}, forgetting the oldest beyond {@link #SIZE} (EvictingQueue). */
    public void add(ItemKey item) {
        if (eaten.size() == SIZE) {
            eaten.removeFirst();
        }
        eaten.addLast(item);
    }

    /** MC getLastEaten: the food eaten last; empty before any meal. */
    public Optional<ItemKey> last() {
        return Optional.ofNullable(eaten.peekLast());
    }

    /** MC checkLastEaten: the last index of {@code item} in the history (0 = oldest), -1 if it is not there. */
    public int lastIndexOf(ItemKey item) {
        int found = -1;
        int index = 0;
        for (ItemKey food : eaten) {
            if (food.equals(item)) {
                found = index;
            }
            index++;
        }
        return found;
    }

    /** MC getFoodHappinessStats: distinct foods (at least 1) and dishes ({@link FoodRules#isDish}). */
    public Stats stats(FoodCatalog catalog) {
        Set<ItemKey> distinct = new HashSet<>();
        int dishes = 0;
        for (ItemKey food : eaten) {
            if (FoodRules.isDish(catalog, food)) {
                dishes++;
            }
            distinct.add(food);
        }
        return new Stats(Math.max(1, distinct.size()), dishes);
    }

    /** MC hasFullFoodHistory: enough meals for a fair judgement. */
    public boolean isFull() {
        return eaten.size() >= SIZE;
    }

    /** The foods noted, oldest first; for saving and the views. */
    public List<ItemKey> foods() {
        return List.copyOf(eaten);
    }
}
