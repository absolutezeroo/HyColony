package dev.hycolony.core.testing.food;

import dev.hycolony.core.building.Building;
import dev.hycolony.core.citizen.food.EatingRule;
import dev.hycolony.core.colony.Colony;
import dev.hycolony.core.kernel.item.ItemKey;

/** A hut module refusing one food to the hut's workers, as MC's builder keeps its resources. */
public final class FakeEatingRule implements EatingRule {
    public ItemKey refused = new ItemKey("none");

    @Override
    public boolean canEat(Colony colony, Building hut, ItemKey food) {
        return !food.equals(refused);
    }
}
