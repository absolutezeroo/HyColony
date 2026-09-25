package dev.hycolony.core.building;

/** Ticked on the colony slow tick (every 500 ticks). */
public interface TickingModule extends BuildingModule {
    void onColonyTick(Building building);
}
