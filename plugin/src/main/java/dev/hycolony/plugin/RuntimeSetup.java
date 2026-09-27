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
    /** id-map.json merges key by key inside its sections (items, blocks...). */
    private static final int ID_MAP_DEPTH = 1;
    /** styles.json merges key by key inside a style and its building types; a level is defined once. */
    private static final int STYLES_DEPTH = 2;

    /** The core's types then the enabled packs' ones, registered once on fresh registries. */
    static RuntimeSetup create(ColonyConfig config, SubPlugins packs) {
        IdMap ids = IdMap.of(packs.merged("id-map.json", ID_MAP_DEPTH));
        PrefabStyles styles = PrefabStyles.of(packs.merged("styles.json", STYLES_DEPTH));
        BuildingRegistry buildings = new BuildingRegistry();
        JobRegistry jobs = new JobRegistry();
        CoreFeatures.register(buildings, jobs);
        packs.registerFeatures(buildings, jobs);
        return new RuntimeSetup(config, ids, styles, buildings, jobs);
    }
}
