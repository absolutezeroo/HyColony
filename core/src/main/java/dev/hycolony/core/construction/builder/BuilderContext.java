package dev.hycolony.core.construction.builder;

import dev.hycolony.core.building.Building;
import dev.hycolony.core.citizen.CitizenData;
import dev.hycolony.core.citizen.Skill;
import dev.hycolony.core.colony.Colony;
import dev.hycolony.core.construction.resources.BuildingResourcesModule;
import dev.hycolony.core.job.Job;
import dev.hycolony.core.job.JobXp;
import dev.hycolony.core.job.WorkerModule;
import dev.hycolony.core.kernel.BlockPos;
import dev.hycolony.core.kernel.port.BodyId;
import dev.hycolony.core.kernel.port.CitizenBodies;
import dev.hycolony.core.kernel.port.ItemCatalog;
import dev.hycolony.core.kernel.port.WorldBlocks;

/**
 * What one builder's AI and its steps share (MC AbstractEntityAIBasic's worker, job and building): the builder, its
 * hut, and the collaborators that hold its items, walks, gestures and structure. {@code hut}, {@code stock} and
 * {@code resources} are null for a builder without a hut, whose AI never runs.
 */
record BuilderContext(
        Colony colony,
        CitizenData citizen,
        Building hut,
        Job job,
        WorldBlocks blocks,
        ItemCatalog catalog,
        BuildingResourcesModule resources,
        BuilderStock stock,
        BuilderWalker walker,
        BuilderGestures gestures,
        BuildSite site,
        StructureScan scan,
        Skill primary,
        Skill secondary) {

    /** The context of {@code citizen}'s builder AI, around the hut it works at (if any). */
    static BuilderContext of(Colony colony, CitizenData citizen, BodyId body) {
        Building hut = citizen.workBuilding() == null
                ? null
                : colony.buildings().at(citizen.workBuilding()).orElse(null);
        CitizenBodies bodies = colony.context().bodies();
        WorldBlocks blocks = colony.context().ports().blocks();
        ItemCatalog catalog = colony.context().ports().catalog();
        BuildingResourcesModule resources =
                hut == null ? null : hut.module(BuildingResourcesModule.class).orElse(null);
        WorkerModule worker =
                hut == null ? null : hut.module(WorkerModule.class).orElse(null);
        return new BuilderContext(
                colony,
                citizen,
                hut,
                citizen.job().orElse(null),
                blocks,
                catalog,
                resources,
                hut == null ? null : new BuilderStock(colony, citizen, hut),
                new BuilderWalker(bodies, body, colony.context().clock()::currentTick),
                new BuilderGestures(bodies, body),
                new BuildSite(colony, resources, new WorkSpot(blocks, catalog)),
                new StructureScan(colony, blocks, catalog),
                worker == null ? Skill.Adaptability : worker.primary(),
                worker == null ? Skill.Athletics : worker.secondary());
    }

    boolean walkToHut() {
        return walker.walkTo(hut.position());
    }

    /** Walks to where the builder stands to work on {@code block} (MC walkToConstructionSite). */
    boolean walkToWork(BlockPos block) {
        return walker.walkToWorkPos(block, () -> site.workSpot(block));
    }

    /** The builder's job experience for one action (MC CitizenExperienceHandler.addExperience). */
    void award(double xp) {
        int homeLevel = citizen.homeBuilding() == null
                ? 0
                : colony.buildings()
                        .at(citizen.homeBuilding())
                        .map(Building::level)
                        .orElse(0);
        JobXp.award(citizen, primary, secondary, xp, new JobXp.Levels(hut.level(), homeLevel));
    }
}
