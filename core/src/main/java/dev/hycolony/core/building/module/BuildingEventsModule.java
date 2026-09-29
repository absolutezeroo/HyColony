package dev.hycolony.core.building.module;

import dev.hycolony.core.building.Building;
import dev.hycolony.core.colony.Colony;

/** A module told about its building's life events (MC {@code IBuildingEventsModule}). */
public interface BuildingEventsModule extends BuildingModule {
    /** The building is being removed from the colony (MC {@code onDestroyed}, from AbstractBuilding.onDestroyed). */
    default void onRemoved(Colony colony, Building building) {}

    /** The building reached {@code newLevel} (MC {@code onUpgradeComplete}, from AbstractBuilding.onUpgradeComplete). */
    default void onUpgradeComplete(Colony colony, Building building, int newLevel) {}
}
