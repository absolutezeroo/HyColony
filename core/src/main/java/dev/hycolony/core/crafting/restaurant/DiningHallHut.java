package dev.hycolony.core.crafting.restaurant;

import dev.hycolony.core.building.BuildingRegistry;
import dev.hycolony.core.building.BuildingType;
import dev.hycolony.core.building.module.ModuleProducer;
import dev.hycolony.core.citizen.Skill;
import dev.hycolony.core.crafting.furnace.FuelListModule;
import dev.hycolony.core.crafting.furnace.FurnaceUserModule;
import dev.hycolony.core.job.JobRegistry;
import dev.hycolony.core.job.WorkerModule;
import java.util.List;

/**
 * The dining hall type (MC BuildingCook, modules from ModBuildingsInitializer): one waiter at every level
 * (Adaptability, then Knowledge, working in the rain), its campfires, its fuels and its menu, then its room (seats and
 * customers).
 * MC's building statistics module is not ported.
 */
public final class DiningHallHut {
    public static final String TYPE_ID = "hycolony:cook";

    /** MC BuildingCook.MAX_BUILDING_LEVEL. */
    public static final int MAX_LEVEL = 5;

    public static final BuildingType TYPE = new BuildingType(
            TYPE_ID,
            "hut.cook",
            MAX_LEVEL,
            List.of(
                    new ModuleProducer(
                            "worker",
                            () -> new WorkerModule(CookJob.TYPE, Skill.Adaptability, Skill.Knowledge, 1, false)
                                    .workingInRain()),
                    new ModuleProducer("furnaces", FurnaceUserModule::new),
                    new ModuleProducer("fuel", FuelListModule::new),
                    new ModuleProducer("menu", RestaurantMenuModule::new),
                    new ModuleProducer("room", DiningRoomModule::new)));

    private DiningHallHut() {}

    /** Registers the dining hall type. */
    public static void register(BuildingRegistry r) {
        r.register(TYPE);
    }

    /** Registers the waiter's job, so saved waiters get their job back on load. */
    public static void register(JobRegistry r) {
        r.register(CookJob.TYPE);
    }
}
