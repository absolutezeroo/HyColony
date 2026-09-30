package dev.hycolony.core.construction.builder;

import dev.hycolony.core.building.Building;
import dev.hycolony.core.citizen.CitizenData;
import dev.hycolony.core.citizen.Skill;
import dev.hycolony.core.citizen.vitals.CitizenWalkReports;
import dev.hycolony.core.colony.Colony;
import dev.hycolony.core.construction.blueprint.Blueprint;
import dev.hycolony.core.construction.blueprint.BlueprintSource;
import dev.hycolony.core.construction.blueprint.StructurePlan;
import dev.hycolony.core.construction.resources.BuildingResourcesModule;
import dev.hycolony.core.construction.shared.BuilderSettingsModule;
import dev.hycolony.core.crafting.recipe.RecipeCatalog;
import dev.hycolony.core.job.Job;
import dev.hycolony.core.job.JobXp;
import dev.hycolony.core.job.WorkerModule;
import dev.hycolony.core.job.work.SyncRequests;
import dev.hycolony.core.job.work.ToolRequests;
import dev.hycolony.core.job.work.WorkerStock;
import dev.hycolony.core.kernel.BlockPos;
import dev.hycolony.core.kernel.port.BodyId;
import dev.hycolony.core.kernel.port.CitizenBodies;
import dev.hycolony.core.kernel.port.ItemCatalog;
import dev.hycolony.core.kernel.port.WorldBlocks;
import java.util.Optional;

/**
 * What one builder's AI and its steps share (MC AbstractEntityAIBasic's worker, job and building): the builder, its
 * hut, and the collaborators that hold its items, requests, walks, gestures and structure.
 */
record BuilderContext(
        Colony colony,
        CitizenData citizen,
        Building hut,
        Job job,
        WorldBlocks blocks,
        ItemCatalog catalog,
        BuildingResourcesModule resources,
        WorkerStock stock,
        BuilderRequests requests,
        SyncRequests sync,
        ToolRequests tools,
        BuilderWalker walker,
        BuilderGestures gestures,
        BuildSite site,
        StructureScan scan,
        Skill primary,
        Skill secondary) {

    /** MC EntityAIStructureBuilder.ACTIONS_UNTIL_DUMP (the builder's own, not CitizenConstants' 32 for others). */
    static final int ACTIONS_UNTIL_DUMP = 4096;

    /**
     * The context of {@code job}'s AI at the hut its citizen works at; empty without that hut or the hut's resources
     * module (the job then gets an {@code IdleAI}).
     */
    static Optional<BuilderContext> of(Colony colony, Job job, BodyId body) {
        Optional<Building> hut = job.hut(colony);
        Optional<BuildingResourcesModule> resources = hut.flatMap(h -> h.module(BuildingResourcesModule.class));
        if (hut.isEmpty() || resources.isEmpty()) {
            return Optional.empty();
        }
        return Optional.of(at(colony, job, hut.get(), resources.get(), body));
    }

    private static BuilderContext at(
            Colony colony, Job job, Building hut, BuildingResourcesModule resources, BodyId body) {
        CitizenData citizen = job.citizen();
        CitizenBodies bodies = colony.context().bodies();
        WorldBlocks blocks = colony.context().ports().blocks();
        ItemCatalog catalog = colony.context().ports().catalog();
        Optional<WorkerModule> worker = hut.module(WorkerModule.class);
        WorkerStock stock = new WorkerStock(colony, citizen, hut, ACTIONS_UNTIL_DUMP);
        return new BuilderContext(
                colony,
                citizen,
                hut,
                job,
                blocks,
                catalog,
                resources,
                stock,
                new BuilderRequests(colony, citizen, hut),
                new SyncRequests(colony, citizen, hut, stock),
                new ToolRequests(colony, citizen, hut),
                new BuilderWalker(
                        bodies,
                        body,
                        colony.context().clock()::currentTick,
                        colony.context().ports(),
                        new CitizenWalkReports(colony, citizen)),
                new BuilderGestures(bodies, body, colony.context().ports().effects()),
                new BuildSite(colony, resources, new WorkSpot(blocks, catalog)),
                new StructureScan(colony, blocks, catalog),
                worker.map(WorkerModule::primary).orElse(Skill.Adaptability),
                worker.map(WorkerModule::secondary).orElse(Skill.Athletics));
    }

    /**
     * The plan of {@code bp} at {@code at}, whose fill cells get the hut's fill block, else the blueprint source's
     * default (MC AbstractEntityAIStructure.getSolidSubstitution); without either they get none.
     */
    StructurePlan planFor(Blueprint bp, BlockPos at) {
        BlueprintSource blueprints = colony.context().ports().blueprints();
        return hut.module(BuilderSettingsModule.class)
                .flatMap(s -> s.fillBlock(blueprints))
                .or(blueprints::defaultFillBlock)
                .map(block -> StructurePlan.build(bp, at, catalog, block))
                .orElseGet(() -> StructurePlan.build(bp, at, catalog));
    }

    /** The Hytale recipes and benches, for what a plan's bench costs. */
    RecipeCatalog recipes() {
        return colony.context().ports().crafting().catalog();
    }

    /** MC walkToBuilding: true once beside the hut block. */
    boolean walkToHut() {
        return walker.walkToBuilding(hut);
    }

    /** MC walkToWorkPos: true once within 7 blocks of {@code pos} in the hut, where the builder dumps and fetches. */
    boolean walkToWorkPos(BlockPos pos) {
        return walker.walkToPosInBuilding(pos, hut);
    }

    /** Walks to where the builder stands to work on {@code block} (MC walkToConstructionSite). */
    boolean walkToWork(BlockPos block) {
        return walker.walkToWorkPos(block, () -> site.workSpot(block));
    }

    /** The builder's job experience for one action (MC CitizenExperienceHandler.addExperience). */
    void award(double xp) {
        JobXp.award(citizen, primary, secondary, xp, JobXp.levels(colony, citizen, hut.level()));
    }
}
