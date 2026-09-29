package dev.hycolony.core.construction.hut;

import dev.hycolony.core.building.Building;
import dev.hycolony.core.building.module.BuildingModule;

/** Residence capacity: as many citizens as the building's level. Used from SP4 on. */
public final class LivingModule implements BuildingModule {
    public int capacity(Building b) {
        return b.level();
    }
}
