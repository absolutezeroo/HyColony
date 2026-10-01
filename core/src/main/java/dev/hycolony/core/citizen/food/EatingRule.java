package dev.hycolony.core.citizen.food;

import dev.hycolony.core.building.Building;
import dev.hycolony.core.building.module.BuildingModule;
import dev.hycolony.core.colony.Colony;
import dev.hycolony.core.kernel.item.ItemKey;

/**
 * A hut module's say on food: what its workers may not eat (MC IBuilding.canEat overrides: the builder's resources,
 * the courier's delivery) and whether the hut keeps food for them (MC AbstractBuilding.keepFood).
 */
public interface EatingRule extends BuildingModule {
    /** MC IBuilding.canEat: whether a worker of {@code hut} may eat {@code food}; true by default. */
    default boolean canEat(Colony colony, Building hut, ItemKey food) {
        return true;
    }

    /** MC AbstractBuilding.keepFood: whether {@code hut} keeps food for its workers; true by default. */
    default boolean keepsFood() {
        return true;
    }
}
