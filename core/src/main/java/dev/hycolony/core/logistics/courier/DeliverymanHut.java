package dev.hycolony.core.logistics.courier;

import dev.hycolony.core.building.Building;
import dev.hycolony.core.building.BuildingRegistry;
import dev.hycolony.core.building.BuildingType;
import dev.hycolony.core.building.ModuleProducer;
import dev.hycolony.core.citizen.Skill;
import dev.hycolony.core.job.JobRegistry;
import dev.hycolony.core.job.WorkerModule;
import dev.hycolony.core.kernel.item.Inventory;
import java.util.List;

/**
 * The courier hut type (MC {@code BuildingDeliveryman}, modules from {@code ModBuildingsInitializer}): one courier,
 * Agility then Adaptability, its task list, and the hut level caps what it carries on a pickup.
 */
public final class DeliverymanHut {
    public static final String TYPE_ID = "hycolony:deliveryman";
    /** MC {@code CONST_DEFAULT_MAX_BUILDING_LEVEL}. */
    public static final int MAX_LEVEL = 5;

    /** MC {@code COURIER_WORK}: {@code DeliverymanAssignmentModule(delivery, Agility, Adaptability, size 1)}. */
    public static final BuildingType TYPE = new BuildingType(
            TYPE_ID,
            "hut.deliveryman",
            MAX_LEVEL,
            List.of(
                    new ModuleProducer(
                            "worker",
                            () -> new WorkerModule(DeliverymanJob.TYPE, Skill.Agility, Skill.Adaptability, 1, false)),
                    new ModuleProducer("courierTaskView", CourierTaskListModule::new)));

    private DeliverymanHut() {}

    /** Registers the courier hut type. */
    public static void register(BuildingRegistry r) {
        r.register(TYPE);
    }

    /** Registers the courier job, so saved couriers get their job back on load. */
    public static void register(JobRegistry r) {
        r.register(DeliverymanJob.TYPE);
    }

    /**
     * MC {@code EntityAIWorkDeliveryman.cannotHoldMoreItems}: below the hut's max level, a courier holding
     * {@code 2^(level - 1) + 1} stacks or more takes nothing more on a pickup (2, 3, 5, 9 stacks); never at max level.
     */
    public static boolean cannotHoldMoreItems(Building hut, Inventory inventory) {
        if (hut.level() >= hut.type().maxLevel()) {
            return false;
        }
        int stacks = inventory.size() - inventory.freeSlots();
        return stacks >= Math.pow(2, hut.level() - 1.0) + 1;
    }
}
