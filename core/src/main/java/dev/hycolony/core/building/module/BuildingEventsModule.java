package dev.hycolony.core.building.module;

import dev.hycolony.core.building.Building;
import dev.hycolony.core.colony.Colony;
import dev.hycolony.core.kernel.BlockPos;
import dev.hycolony.core.kernel.item.BlockKey;

/** A module told about its building's life events (MC {@code IBuildingEventsModule}). */
public interface BuildingEventsModule extends BuildingModule {
    /** The building is being removed from the colony (MC {@code onDestroyed}, from AbstractBuilding.onDestroyed). */
    default void onRemoved(Colony colony, Building building) {}

    /**
     * The building reached {@code newLevel} (MC {@code onUpgradeComplete}, from AbstractBuilding.onUpgradeComplete).
     */
    default void onUpgradeComplete(Colony colony, Building building, int newLevel) {}

    /**
     * A block of its plan was placed at {@code pos} in the building, or found there as planned (MC
     * {@code onBlockPlacedInBuilding}, from AbstractBuilding.registerBlockPosition).
     */
    default void onBlockPlacedInBuilding(Colony colony, Building building, BlockPos pos, BlockKey block) {}

    /** One of the building's citizens got up after a night in bed (MC {@code onWakeUp}, from AbstractBuilding). */
    default void onWakeUp(Colony colony, Building building) {}

    /** MC AbstractBuilding.onWakeUp: tells every events module of {@code building}. */
    static void wakeUp(Colony colony, Building building) {
        for (BuildingModule module : building.modules().values()) {
            if (module instanceof BuildingEventsModule events) {
                events.onWakeUp(colony, building);
            }
        }
    }

    /** MC registerBlockPosition: tells each events module of {@code building} about the block placed at {@code pos}. */
    static void blockPlaced(Colony colony, Building building, BlockPos pos, BlockKey block) {
        for (BuildingModule module : building.modules().values()) {
            if (module instanceof BuildingEventsModule events) {
                events.onBlockPlacedInBuilding(colony, building, pos, block);
            }
        }
    }
}
