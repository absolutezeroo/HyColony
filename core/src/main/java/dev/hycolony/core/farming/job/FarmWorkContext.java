package dev.hycolony.core.farming.job;

import dev.hycolony.core.building.Building;
import dev.hycolony.core.citizen.CitizenData;
import dev.hycolony.core.colony.BlockApproach;
import dev.hycolony.core.colony.Colony;
import dev.hycolony.core.crafting.job.CraftingWorkContext;
import dev.hycolony.core.farming.FarmingAccess;
import dev.hycolony.core.farming.hut.FarmerFieldsModule;
import dev.hycolony.core.farming.hut.FarmerSettingsModule;
import dev.hycolony.core.job.JobXp;
import dev.hycolony.core.job.WorkerModule;
import dev.hycolony.core.job.work.ToolRequests;
import dev.hycolony.core.job.work.WorkerHands;
import dev.hycolony.core.job.work.WorkerStock;
import dev.hycolony.core.kernel.nav.BodyWalker;
import java.util.Optional;

/**
 * What the farmer's {@link FarmWork} works with (MC EntityAIWorkFarmer's worker, job and building): its hut and the
 * hut's farming modules, and the shared worker parts that hold its items, its tool requests, its walks and its hands.
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
        BlockApproach approach,
        WorkerHands hands) {
    /** MC MAX_BLOCKS_MINED: the actions after which the farmer empties its inventory at the hut. */
    static final int ACTIONS_UNTIL_DUMP = 64;

    /**
     * The context of {@code job}'s farm work at the hut of its crafting work, sharing that work's items, tool requests
     * and walks (one body, one walker: two would each trust the other's nav status); empty without the farmer modules
     * there.
     */
    static Optional<FarmWorkContext> of(CraftingWorkContext crafting, FarmerJob job) {
        Building hut = crafting.hut();
        Optional<FarmerFieldsModule> fields = hut.module(FarmerFieldsModule.class);
        Optional<FarmerSettingsModule> settings = hut.module(FarmerSettingsModule.class);
        Optional<WorkerModule> workers = hut.module(WorkerModule.class);
        if (fields.isEmpty() || settings.isEmpty() || workers.isEmpty()) {
            return Optional.empty();
        }
        Colony colony = crafting.colony();
        return Optional.of(new FarmWorkContext(
                colony,
                job,
                hut,
                fields.get(),
                settings.get(),
                workers.get(),
                crafting.stock(),
                crafting.tools(),
                crafting.walker(),
                crafting.approach(),
                new WorkerHands(colony.context().bodies(), crafting.body())));
    }

    CitizenData citizen() {
        return job.citizen();
    }

    FarmingAccess farming() {
        return colony.context().ports().farming();
    }

    /** MC walkToBuilding: true once beside the hut block ({@link BlockApproach}). */
    boolean walkToHut() {
        return approach.walkToBuilding(hut);
    }

    /** MC CitizenExperienceHandler.addExperience, split between the hut's primary and secondary skills. */
    void award(double xp) {
        JobXp.award(
                citizen(), workers.primary(), workers.secondary(), xp, JobXp.levels(colony, citizen(), hut.level()));
    }
}
