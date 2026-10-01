package dev.hycolony.core.construction.hut;

import dev.hycolony.core.building.BuildingRegistry;
import dev.hycolony.core.building.BuildingType;
import dev.hycolony.core.building.module.ModuleProducer;
import dev.hycolony.core.citizen.Skill;
import dev.hycolony.core.citizen.home.LivingModule;
import dev.hycolony.core.construction.builder.BuilderJob;
import dev.hycolony.core.construction.resources.BuildingResourcesModule;
import dev.hycolony.core.construction.shared.BuilderHut;
import dev.hycolony.core.construction.shared.BuilderSettingsModule;
import dev.hycolony.core.job.JobRegistry;
import dev.hycolony.core.job.WorkerModule;
import dev.hycolony.core.kernel.item.ToolType;
import dev.hycolony.core.logistics.pickup.KeepToolsModule;
import java.util.EnumSet;
import java.util.List;

/** The hut types of the construction system (builder and residence) with their modules. MC ModBuildings. */
public final class ConstructionBuildingTypes {
    public static final BuildingType BUILDER = new BuildingType(
            BuilderHut.TYPE_ID,
            "hut.builder",
            BuilderHut.MAX_LEVEL,
            List.of(
                    new ModuleProducer(
                            "worker",
                            () -> new WorkerModule(BuilderJob.TYPE, Skill.Adaptability, Skill.Athletics, 1, true)),
                    new ModuleProducer("resources", BuildingResourcesModule::new),
                    new ModuleProducer("builderSettings", BuilderSettingsModule::new),
                    new ModuleProducer("workOrderList", WorkOrderListModule::new),
                    // MC BuildingBuilder keepX: pickaxe, shovel, axe, hoe and shears. Deviation from MC: no shears,
                    // HyColony has no such tool type yet.
                    new ModuleProducer(
                            "keepTools",
                            () -> new KeepToolsModule(
                                    EnumSet.of(ToolType.PICKAXE, ToolType.AXE, ToolType.SHOVEL, ToolType.HOE)))));

    public static final BuildingType RESIDENCE = new BuildingType(
            "hycolony:residence", "hut.residence", 5, List.of(new ModuleProducer("living", LivingModule::new)));

    private ConstructionBuildingTypes() {}

    /** Registers the builder and residence hut types. */
    public static void register(BuildingRegistry r) {
        r.register(BUILDER);
        r.register(RESIDENCE);
    }

    /** The construction jobs, so saved builders get their job back on load. */
    public static void register(JobRegistry r) {
        r.register(BuilderJob.TYPE);
    }
}
