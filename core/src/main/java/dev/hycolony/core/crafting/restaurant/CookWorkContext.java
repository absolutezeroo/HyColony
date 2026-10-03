package dev.hycolony.core.crafting.restaurant;

import dev.hycolony.core.building.Building;
import dev.hycolony.core.citizen.CitizenData;
import dev.hycolony.core.citizen.vitals.CitizenWalkReports;
import dev.hycolony.core.colony.BlockApproach;
import dev.hycolony.core.colony.Colony;
import dev.hycolony.core.crafting.furnace.CookingStations;
import dev.hycolony.core.crafting.furnace.FuelListModule;
import dev.hycolony.core.crafting.furnace.FurnaceUserModule;
import dev.hycolony.core.job.JobXp;
import dev.hycolony.core.job.WorkerModule;
import dev.hycolony.core.job.work.WorkerStock;
import dev.hycolony.core.kernel.BlockPos;
import dev.hycolony.core.kernel.catalog.FoodCatalog;
import dev.hycolony.core.kernel.catalog.ItemCatalog;
import dev.hycolony.core.kernel.nav.BodyWalker;
import dev.hycolony.core.kernel.port.BodyId;
import java.util.Optional;

/**
 * What the waiter works with (MC EntityAIWorkCook's worker, job and building): its dining hall and the hall's modules,
 * its items and its walks.
 */
record CookWorkContext(
        Colony colony,
        CookJob job,
        Building hall,
        BodyId body,
        RestaurantMenuModule menu,
        FuelListModule fuel,
        FurnaceUserModule furnaces,
        WorkerModule workers,
        WorkerStock stock,
        BlockApproach approach) {
    /** MC getActionsDoneUntilDumping: the waiter empties its inventory after each action. */
    static final int ACTIONS_UNTIL_DUMP = 1;

    /** The context of {@code job}'s work at its dining hall; empty without one, or without its modules. */
    static Optional<CookWorkContext> of(Colony colony, CookJob job, BodyId body) {
        Building hall = job.hut(colony).orElse(null);
        if (hall == null) {
            return Optional.empty();
        }
        Optional<RestaurantMenuModule> menu = hall.module(RestaurantMenuModule.class);
        Optional<FuelListModule> fuel = hall.module(FuelListModule.class);
        Optional<FurnaceUserModule> furnaces = hall.module(FurnaceUserModule.class);
        Optional<WorkerModule> workers = hall.module(WorkerModule.class);
        if (menu.isEmpty() || fuel.isEmpty() || furnaces.isEmpty() || workers.isEmpty()) {
            return Optional.empty();
        }
        CitizenData citizen = job.citizen();
        BodyWalker walker = new BodyWalker(
                colony.context().bodies(),
                body,
                colony.context().clock()::currentTick,
                new CitizenWalkReports(colony, citizen));
        return Optional.of(new CookWorkContext(
                colony,
                job,
                hall,
                body,
                menu.get(),
                fuel.get(),
                furnaces.get(),
                workers.get(),
                new WorkerStock(colony, citizen, hall, ACTIONS_UNTIL_DUMP),
                new BlockApproach(colony.context().ports(), walker)));
    }

    CitizenData citizen() {
        return job.citizen();
    }

    ItemCatalog items() {
        return colony.context().ports().catalog();
    }

    FoodCatalog foods() {
        return colony.context().ports().foods();
    }

    CookingStations stations() {
        return colony.context().ports().cooking().stations();
    }

    /** MC walkToBuilding: true once beside the hall's block. */
    boolean walkToHall() {
        return approach.walkToBuilding(hall);
    }

    /** MC walkToWorkPos: true once within {@link BlockApproach#WORK_IN_BUILDING_REACH} of {@code pos} in the hall. */
    boolean walkToWorkPos(BlockPos pos) {
        return approach.walkToPosInBuilding(pos, hall, BlockApproach.WORK_IN_BUILDING_REACH);
    }

    /** MC CitizenExperienceHandler.addExperience, split between the hall's primary and secondary skills. */
    void award(double xp) {
        JobXp.award(
                citizen(), workers.primary(), workers.secondary(), xp, JobXp.levels(colony, citizen(), hall.level()));
    }
}
