package dev.hycolony.core.construction;

import dev.hycolony.core.building.BuildingRegistry;
import dev.hycolony.core.building.BuildingType;
import dev.hycolony.core.building.ModuleProducer;
import dev.hycolony.core.citizen.CitizenData;
import dev.hycolony.core.citizen.Skill;
import dev.hycolony.core.colony.Colony;
import dev.hycolony.core.job.Job;
import dev.hycolony.core.job.JobAI;
import dev.hycolony.core.job.JobType;
import dev.hycolony.core.job.WorkerModule;
import dev.hycolony.core.kernel.port.BodyId;
import java.util.List;

public final class ConstructionBuildingTypes {
    /**
     * Placeholder job type: task 9 replaces this factory with the real builder job.
     * Not reachable except via hiring in task 6 ({@link WorkerModule#hire} only constructs the
     * job; it never calls {@link Job#createAI}).
     */
    public static final JobType BUILDER_JOB = new JobType("hycolony:builder", PlaceholderBuilderJob::new);

    public static final BuildingType BUILDER = new BuildingType("hycolony:builder", "hut.builder", 5, List.of(
            new ModuleProducer("worker", () -> new WorkerModule(BUILDER_JOB, Skill.Adaptability, Skill.Athletics, 1, true)),
            new ModuleProducer("builderSettings", BuilderSettingsModule::new),
            new ModuleProducer("resources", BuildingResourcesModule::new)
    ));

    public static final BuildingType RESIDENCE = new BuildingType("hycolony:residence", "hut.residence", 5, List.of(
            new ModuleProducer("living", LivingModule::new)
    ));

    private ConstructionBuildingTypes() {}

    public static void register(BuildingRegistry r) {
        r.register(BUILDER);
        r.register(RESIDENCE);
    }

    private static final class PlaceholderBuilderJob extends Job {
        PlaceholderBuilderJob(CitizenData citizen) { super(BUILDER_JOB, citizen); }

        @Override
        public JobAI createAI(Colony colony, BodyId body) {
            throw new UnsupportedOperationException("builder AI arrives in task 9");
        }
    }
}
