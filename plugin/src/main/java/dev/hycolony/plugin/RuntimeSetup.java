package dev.hycolony.plugin;

import dev.hycolony.core.CoreFeatures;
import dev.hycolony.core.building.BuildingRegistry;
import dev.hycolony.core.job.JobRegistry;
import dev.hycolony.core.kernel.config.ColonyConfig;

/**
 * What every world's runtime and every hut system share, built once at plugin setup: the config, the asset ids and
 * the one building and job registry (a hut is recognised by the same registry that the colonies use).
 */
public record RuntimeSetup(ColonyConfig config, IdMap ids, BuildingRegistry buildings, JobRegistry jobs) {

    /** The core's types, registered once on fresh registries. */
    static RuntimeSetup create(ColonyConfig config, IdMap ids) {
        BuildingRegistry buildings = new BuildingRegistry();
        JobRegistry jobs = new JobRegistry();
        CoreFeatures.register(buildings, jobs);
        return new RuntimeSetup(config, ids, buildings, jobs);
    }
}
