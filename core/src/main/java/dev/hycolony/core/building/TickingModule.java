package dev.hycolony.core.building;

import dev.hycolony.core.colony.Colony;

/** Ticked on the colony slow tick (every 500 ticks). */
public interface TickingModule extends BuildingModule {
    void onColonyTick(Building building);

    /** Colony-aware overload; modules that need the colony (e.g. auto-hiring) override this instead. */
    default void onColonyTick(Colony colony, Building building) {
        onColonyTick(building);
    }
}
