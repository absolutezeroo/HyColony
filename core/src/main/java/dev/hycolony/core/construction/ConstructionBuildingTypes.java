package dev.hycolony.core.construction;

import dev.hycolony.core.building.BuildingRegistry;
import dev.hycolony.core.building.BuildingType;
import dev.hycolony.core.building.ModuleProducer;
import dev.hycolony.core.citizen.Skill;
import dev.hycolony.core.construction.builder.BuilderJob;
import dev.hycolony.core.construction.resources.BuildingResourcesModule;
import dev.hycolony.core.job.JobRegistry;
import dev.hycolony.core.job.WorkerModule;
import java.util.List;

public final class ConstructionBuildingTypes {
    public static final BuildingType BUILDER = new BuildingType(
            "hycolony:builder",
            "hut.builder",
            5,
            List.of(
                    new ModuleProducer(
                            "worker",
                            () -> new WorkerModule(BuilderJob.TYPE, Skill.Adaptability, Skill.Athletics, 1, true)),
                    new ModuleProducer("builderSettings", BuilderSettingsModule::new),
                    new ModuleProducer("resources", BuildingResourcesModule::new)));

    public static final BuildingType RESIDENCE = new BuildingType(
            "hycolony:residence", "hut.residence", 5, List.of(new ModuleProducer("living", LivingModule::new)));

    private ConstructionBuildingTypes() {}

    public static void register(BuildingRegistry r) {
        r.register(BUILDER);
        r.register(RESIDENCE);
    }

    /** The construction jobs, so saved builders get their job back on load. */
    public static void register(JobRegistry r) {
        r.register(BuilderJob.TYPE);
    }
}
