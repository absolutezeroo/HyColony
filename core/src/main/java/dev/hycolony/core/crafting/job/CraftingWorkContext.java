package dev.hycolony.core.crafting.job;

import dev.hycolony.core.building.Building;
import dev.hycolony.core.building.BuildingModule;
import dev.hycolony.core.citizen.CitizenData;
import dev.hycolony.core.citizen.Skill;
import dev.hycolony.core.colony.Colony;
import dev.hycolony.core.crafting.recipe.RecipeCatalog;
import dev.hycolony.core.crafting.task.Crafter;
import dev.hycolony.core.crafting.task.CraftingTasks;
import dev.hycolony.core.job.Job;
import dev.hycolony.core.job.JobXp;
import dev.hycolony.core.job.WorkerModule;
import dev.hycolony.core.job.work.SyncRequests;
import dev.hycolony.core.job.work.ToolRequests;
import dev.hycolony.core.job.work.WorkerStock;
import dev.hycolony.core.kernel.nav.BodyWalker;
import dev.hycolony.core.kernel.port.BodyId;
import dev.hycolony.core.kernel.port.ItemCatalog;
import java.util.Optional;

/**
 * What a crafter's {@link CraftingWork} works with (MC AbstractEntityAIBasic's worker, job and building): the crafter's
 * job and hut, and the shared worker parts that hold its items, requests and walks.
 */
public record CraftingWorkContext(
        Colony colony,
        Job job,
        Crafter crafter,
        Building hut,
        BodyId body,
        WorkerStock stock,
        ToolRequests tools,
        SyncRequests requests,
        BodyWalker walker,
        CraftingSkills skills) {

    /**
     * The skills of the crafter's worker module (MC CraftingWorkerBuildingModule): {@code primary} and {@code
     * secondary} share its experience; {@code speed} shortens a run and {@code improvement} improves recipes, the
     * primary and the secondary skill unless a crafter says otherwise.
     */
    public record CraftingSkills(Skill primary, Skill secondary, Skill speed, Skill improvement) {
        /** MC's default: the speed skill is the primary one, the improvement skill the secondary one. */
        public static CraftingSkills of(WorkerModule workers) {
            return new CraftingSkills(workers.primary(), workers.secondary(), workers.primary(), workers.secondary());
        }
    }

    /**
     * The context of {@code job}'s crafting work, at the hut its citizen works at, with the skills of that hut's
     * worker module for the job (the first one if none matches); empty without a hut or a worker module there.
     */
    public static <J extends Job & Crafter> Optional<CraftingWorkContext> of(Colony colony, J job, BodyId body) {
        return of(colony, job, body, CraftingWork.ACTIONS_UNTIL_DUMP);
    }

    /**
     * The same, for a crafter that dumps every {@code actionsUntilDump} actions and earns as many per finished or
     * failed task (MC getActionsDoneUntilDumping and getActionRewardForCraftingSuccess, 64 for the farmer).
     */
    public static <J extends Job & Crafter> Optional<CraftingWorkContext> of(
            Colony colony, J job, BodyId body, int actionsUntilDump) {
        Building hut = job.hut(colony).orElse(null);
        Optional<WorkerModule> workers = hut == null ? Optional.empty() : workerModule(hut, job);
        if (hut == null || workers.isEmpty()) {
            return Optional.empty();
        }
        CitizenData citizen = job.citizen();
        WorkerStock stock = new WorkerStock(colony, citizen, hut, actionsUntilDump);
        return Optional.of(new CraftingWorkContext(
                colony,
                job,
                job,
                hut,
                body,
                stock,
                new ToolRequests(colony, citizen, hut),
                new SyncRequests(colony, citizen, hut, stock),
                new BodyWalker(colony.context().bodies(), body, colony.context().clock()::currentTick),
                CraftingSkills.of(workers.get())));
    }

    /** MC getModuleForJob: the hut's worker module for the job, else its first one. */
    private static Optional<WorkerModule> workerModule(Building hut, Job job) {
        WorkerModule first = null;
        for (BuildingModule module : hut.modules().values()) {
            if (module instanceof WorkerModule workers) {
                if (workers.job().id().equals(job.type().id())) {
                    return Optional.of(workers);
                }
                first = first == null ? workers : first;
            }
        }
        return Optional.ofNullable(first);
    }

    public CitizenData citizen() {
        return job.citizen();
    }

    public CraftingTasks tasks() {
        return crafter.craftingTasks();
    }

    RecipeCatalog recipes() {
        return colony.context().ports().crafting().catalog();
    }

    ItemCatalog items() {
        return colony.context().ports().catalog();
    }

    /** MC walkToBuilding: true once at the hut (or once the walk ended anyway). */
    boolean walkToHut() {
        return walker.walkTo(hut.position());
    }

    /** MC CitizenExperienceHandler.addExperience, split between the worker module's primary and secondary skills. */
    void award(double xp) {
        JobXp.award(citizen(), skills.primary(), skills.secondary(), xp, JobXp.levels(colony, citizen(), hut.level()));
    }
}
