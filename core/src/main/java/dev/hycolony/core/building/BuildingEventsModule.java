package dev.hycolony.core.building;

public interface BuildingEventsModule extends BuildingModule {
    default void onPlaced(Building building) {}

    default void onRemoved(Building building) {}

    default void onUpgradeComplete(Building building, int newLevel) {}

    default void onWakeUp(Building building) {}
}
