package dev.hycolony.core.job;

import dev.hycolony.core.building.Building;
import dev.hycolony.core.building.module.BuildingModule;
import dev.hycolony.core.colony.Colony;

/**
 * A module told when a worker module of its building hires a citizen (MC WorkerBuildingModule.onAssignment, which
 * calls its hut's crafting modules). It lets features react to a hiring without {@code job} depending on them.
 */
public interface HiringListener extends BuildingModule {
    /** A worker module of {@code building} has just hired a citizen. */
    void onWorkerHired(Colony colony, Building building);
}
