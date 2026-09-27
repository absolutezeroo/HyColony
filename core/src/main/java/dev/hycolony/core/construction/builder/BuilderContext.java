package dev.hycolony.core.construction.builder;

import dev.hycolony.core.building.Building;
import dev.hycolony.core.citizen.CitizenData;
import dev.hycolony.core.citizen.Skill;
import dev.hycolony.core.colony.Colony;
import dev.hycolony.core.construction.resources.BuildingResourcesModule;
import dev.hycolony.core.job.Job;
import dev.hycolony.core.job.JobXp;
import dev.hycolony.core.job.WorkerModule;
import dev.hycolony.core.job.work.ToolRequests;
import dev.hycolony.core.job.work.WorkerStock;
import dev.hycolony.core.kernel.BlockPos;
import dev.hycolony.core.kernel.port.BodyId;
import dev.hycolony.core.kernel.port.CitizenBodies;
import dev.hycolony.core.kernel.port.ItemCatalog;
import dev.hycolony.core.kernel.port.WorldBlocks;
import java.util.Objects;
import java.util.Optional;
import org.jspecify.annotations.Nullable;

/**
 * What one builder's AI and its steps share (MC AbstractEntityAIBasic's worker, job and building): the builder, its
 * hut, and the collaborators that hold its items, requests, walks, gestures and structure. {@code hut}, {@code job},
 * {@code stock}, {@code requests}, {@code tools} and {@code resources} are null for a builder without a hut, whose AI
 * never runs: their accessors are only called while it runs ({@link #hasHut()} tells).
 */
record BuilderContext(
        Colony colony,
        CitizenData citizen,
        @Nullable Building hut,
        @Nullable Job job,
        WorldBlocks blocks,
        ItemCatalog catalog,
        @Nullable BuildingResourcesModule resources,
        @Nullable WorkerStock stock,
        @Nullable BuilderRequests requests,
        @Nullable ToolRequests tools,
        BuilderWalker walker,
        BuilderGestures gestures,
        BuildSite site,
        StructureScan scan,
        Skill primary,
        Skill secondary) {

    private static final String NO_HUT = "builder without a hut";

    /** MC EntityAIStructureBuilder.ACTIONS_UNTIL_DUMP (the builder's own, not CitizenConstants' 32 for others). */
    static final int ACTIONS_UNTIL_DUMP = 4096;

    /** The context of {@code citizen}'s builder AI, around the hut it works at (if any). */
    static BuilderContext of(Colony colony, CitizenData citizen, BodyId body) {
        Building hut = Optional.ofNullable(citizen.workBuilding())
                .flatMap(colony.buildings()::at)
                .orElse(null);
        CitizenBodies bodies = colony.context().bodies();
        WorldBlocks blocks = colony.context().ports().blocks();
        ItemCatalog catalog = colony.context().ports().catalog();
        BuildingResourcesModule resources =
                hut == null ? null : hut.module(BuildingResourcesModule.class).orElse(null);
        WorkerModule worker =
                hut == null ? null : hut.module(WorkerModule.class).orElse(null);
        WorkerStock stock = null;
        BuilderRequests requests = null;
        ToolRequests tools = null;
        if (hut != null) {
            stock = new WorkerStock(colony, citizen, hut, ACTIONS_UNTIL_DUMP);
            requests = new BuilderRequests(colony, citizen, hut, stock);
            tools = new ToolRequests(colony, citizen, hut);
        }
        return new BuilderContext(
                colony,
                citizen,
                hut,
                citizen.job().orElse(null),
                blocks,
                catalog,
                resources,
                stock,
                requests,
                tools,
                new BuilderWalker(bodies, body, colony.context().clock()::currentTick),
                new BuilderGestures(bodies, body, colony.context().ports().effects()),
                // Without a hut the AI never runs: the site gets a detached module it never touches.
                new BuildSite(
                        colony,
                        resources == null ? new BuildingResourcesModule() : resources,
                        new WorkSpot(blocks, catalog)),
                new StructureScan(colony, blocks, catalog),
                worker == null ? Skill.Adaptability : worker.primary(),
                worker == null ? Skill.Athletics : worker.secondary());
    }

    /** False for a builder without a hut, whose AI never runs. */
    boolean hasHut() {
        return hut != null;
    }

    /** True when the AI can run: a hut with its resources module, and a job. */
    boolean canRun() {
        return hut != null && resources != null && job != null;
    }

    @Override
    public Building hut() {
        return Objects.requireNonNull(hut, NO_HUT);
    }

    @Override
    public Job job() {
        return Objects.requireNonNull(job, NO_HUT);
    }

    @Override
    public BuildingResourcesModule resources() {
        return Objects.requireNonNull(resources, NO_HUT);
    }

    @Override
    public WorkerStock stock() {
        return Objects.requireNonNull(stock, NO_HUT);
    }

    @Override
    public BuilderRequests requests() {
        return Objects.requireNonNull(requests, NO_HUT);
    }

    @Override
    public ToolRequests tools() {
        return Objects.requireNonNull(tools, NO_HUT);
    }

    boolean walkToHut() {
        return walker.walkTo(hut().position());
    }

    /** Walks to where the builder stands to work on {@code block} (MC walkToConstructionSite). */
    boolean walkToWork(BlockPos block) {
        return walker.walkToWorkPos(block, () -> site.workSpot(block));
    }

    /** The builder's job experience for one action (MC CitizenExperienceHandler.addExperience). */
    void award(double xp) {
        int homeLevel = Optional.ofNullable(citizen.homeBuilding())
                .flatMap(colony.buildings()::at)
                .map(Building::level)
                .orElse(0);
        JobXp.award(citizen, primary, secondary, xp, new JobXp.Levels(hut().level(), homeLevel));
    }
}
