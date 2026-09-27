package dev.hycolony.plugin;

import dev.hycolony.core.CoreFeatures;
import dev.hycolony.core.building.BuildingRegistry;
import dev.hycolony.core.job.JobRegistry;
import dev.hycolony.core.kernel.config.ColonyConfig;
import dev.hycolony.plugin.prefab.PrefabStyles;
import dev.hycolony.plugin.subplugin.SubPlugins;

/**
 * What every world's runtime and every hut system share, built once at plugin setup: the config, the asset ids and
 * blueprint styles (core files merged with the enabled sub-plugins' fragments) and the one building and job registry
 * (a hut is recognised by the same registry that the colonies use).
 */
public record RuntimeSetup(
        ColonyConfig config, IdMap ids, PrefabStyles styles, BuildingRegistry buildings, JobRegistry jobs) {
    /** The core's types then the enabled packs' ones, registered once on fresh registries. */
    static RuntimeSetup create(ColonyConfig config, SubPlugins packs) {
        IdMap ids = packs.idMap();
        PrefabStyles styles = packs.styles();
        BuildingRegistry buildings = new BuildingRegistry();
        JobRegistry jobs = new JobRegistry();
        CoreFeatures.register(buildings, jobs);
        packs.registerFeatures(buildings, jobs, ids);
        return new RuntimeSetup(config, ids, styles, buildings, jobs);
    }
}
