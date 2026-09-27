package dev.hycolony.core;

import dev.hycolony.core.building.BuildingRegistry;
import dev.hycolony.core.job.JobRegistry;

/**
 * A sub-plugin's own building and job types, registered once at startup after the core's (MC: an addon registering
 * on ModBuildings / ModJobs). Named by the {@code Registrar} of the pack's manifest and always run through
 * {@link CoreFeatures#registerPack}, so a pack that fails registers nothing.
 */
@FunctionalInterface
public interface FeaturePack {
    /** Registers the pack's types on the given registries, which hold only this pack's types. */
    void register(BuildingRegistry buildings, JobRegistry jobs);
}
