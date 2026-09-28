package dev.hycolony.core.farming.job;

import dev.hycolony.core.building.Building;
import dev.hycolony.core.citizen.CitizenData;
import dev.hycolony.core.colony.Colony;
import dev.hycolony.core.farming.FarmingAccess;
import dev.hycolony.core.farming.hut.FarmerFieldsModule;
import dev.hycolony.core.farming.hut.FarmerSettingsModule;
import dev.hycolony.core.job.JobXp;
import dev.hycolony.core.job.WorkerModule;
import dev.hycolony.core.job.work.ToolRequests;
import dev.hycolony.core.job.work.WorkerStock;
import dev.hycolony.core.kernel.item.ItemAmount;
import dev.hycolony.core.kernel.item.ItemKey;
import dev.hycolony.core.kernel.item.ToolType;
import dev.hycolony.core.kernel.nav.BodyWalker;
import dev.hycolony.core.kernel.port.BodyAnimation;
import dev.hycolony.core.kernel.port.BodyId;
import java.util.Optional;
import java.util.OptionalInt;

/**
 * What the farmer's {@link FarmWork} works with (MC EntityAIWorkFarmer's worker, job and building): its hut and the
 * hut's farming modules, and the shared worker parts that hold its items, its tool requests and its walks.
 */
record FarmWorkContext(
        Colony colony,
        FarmerJob job,
        Building hut,
        FarmerFieldsModule fields,
        FarmerSettingsModule settings,
        WorkerModule workers,
        WorkerStock stock,
        ToolRequests tools,
        BodyWalker walker,
        BodyId body) {
    /** MC MAX_BLOCKS_MINED: the actions after which the farmer empties its inventory at the hut. */
    static final int ACTIONS_UNTIL_DUMP = 64;

    /** The context of {@code job}'s farm work at its hut; empty without a hut or without the farmer modules there. */
    static Optional<FarmWorkContext> of(Colony colony, FarmerJob job, BodyId body) {
        Building hut = Optional.ofNullable(job.citizen().workBuilding())
                .flatMap(colony.buildings()::at)
                .orElse(null);
        if (hut == null) {
            return Optional.empty();
        }
        Optional<FarmerFieldsModule> fields = hut.module(FarmerFieldsModule.class);
        Optional<FarmerSettingsModule> settings = hut.module(FarmerSettingsModule.class);
        Optional<WorkerModule> workers = hut.module(WorkerModule.class);
        if (fields.isEmpty() || settings.isEmpty() || workers.isEmpty()) {
            return Optional.empty();
        }
        CitizenData citizen = job.citizen();
        WorkerStock stock = new WorkerStock(colony, citizen, hut, ACTIONS_UNTIL_DUMP);
        return Optional.of(new FarmWorkContext(
                colony,
                job,
                hut,
                fields.get(),
                settings.get(),
                workers.get(),
                stock,
                new ToolRequests(colony, citizen, hut),
                new BodyWalker(colony.context().bodies(), body, colony.context().clock()::currentTick),
                body));
    }

    CitizenData citizen() {
        return job.citizen();
    }

    FarmingAccess farming() {
        return colony.context().ports().farming();
    }

    /** MC equipHoe: the inventory's hoe in the main hand, nothing without one. */
    void holdHoe() {
        OptionalInt hoe = stock.toolInInventory(ToolType.HOE);
        Optional<ItemKey> item = hoe.isEmpty()
                ? Optional.empty()
                : stock.inventory().slot(hoe.getAsInt()).map(ItemAmount::item);
        colony.context().bodies().setHeldItem(body, item);
    }

    /** MC swing: one stroke of {@code animation}. */
    void swing(BodyAnimation animation) {
        colony.context().bodies().playAnimation(body, animation);
    }

    /** MC walkToBuilding: true once at the hut. */
    boolean walkToHut() {
        return walker.walkTo(hut.position());
    }

    /** MC CitizenExperienceHandler.addExperience, split between the hut's primary and secondary skills. */
    void award(double xp) {
        int homeLevel = Optional.ofNullable(citizen().homeBuilding())
                .flatMap(colony.buildings()::at)
                .map(Building::level)
                .orElse(0);
        JobXp.award(citizen(), workers.primary(), workers.secondary(), xp, new JobXp.Levels(hut.level(), homeLevel));
    }
}
