package dev.hycolony.core.building.module;

import dev.hycolony.core.building.Building;
import dev.hycolony.core.colony.Colony;

/** Ticked on the colony slow tick (every 500 ticks). MC {@code ITickingModule.onColonyTick}. */
public interface TickingModule extends BuildingModule {
    /** Runs this module's slow-tick work for {@code building} in {@code colony}. */
    void onColonyTick(Colony colony, Building building);
}
