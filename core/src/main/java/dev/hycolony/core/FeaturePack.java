package dev.hycolony.core;

import dev.hycolony.core.building.BuildingRegistry;
import dev.hycolony.core.job.JobRegistry;

/**
 * A sub-plugin's own building and job types, registered once at startup right after {@link CoreFeatures#register}
 * (MC: an addon registering on ModBuildings / ModJobs). Named by the {@code Registrar} of the pack's manifest.
 */
@FunctionalInterface
public interface FeaturePack {
    /** Registers the pack's types; an id already registered (a core type included) throws, the registry is kept. */
    void register(BuildingRegistry buildings, JobRegistry jobs);
}
