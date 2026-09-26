package dev.hycolony.plugin.config;

import com.hypixel.hytale.codec.Codec;
import com.hypixel.hytale.codec.KeyedCodec;
import com.hypixel.hytale.codec.builder.BuilderCodec;
import dev.hycolony.core.kernel.config.ColonyConfig;

/** The {@code HyColony} section of config.json: our own options, none of them a MineColonies option. */
final class HyColonySection {
    static final BuilderCodec<HyColonySection> CODEC = BuilderCodec.builder(HyColonySection.class, HyColonySection::new)
            .append(
                    new KeyedCodec<>("AutosaveIntervalMinutes", Codec.INTEGER),
                    (s, v) -> s.autosaveIntervalMinutes = v,
                    s -> s.autosaveIntervalMinutes)
            .add()
            .append(
                    new KeyedCodec<>("BuilderInfiniteResources", Codec.BOOLEAN),
                    (s, v) -> s.builderInfiniteResources = v,
                    s -> s.builderInfiniteResources)
            .add()
            .append(
                    new KeyedCodec<>("CreativeOperatorFreeBuilds", Codec.BOOLEAN),
                    (s, v) -> s.creativeOperatorFreeBuilds = v,
                    s -> s.creativeOperatorFreeBuilds)
            .add()
            .build();

    private static final ColonyConfig.HyColony DEFAULTS =
            ColonyConfig.defaults().hycolony();

    int autosaveIntervalMinutes = DEFAULTS.autosaveIntervalMinutes();
    boolean builderInfiniteResources = DEFAULTS.builderInfiniteResources();
    boolean creativeOperatorFreeBuilds = DEFAULTS.creativeOperatorFreeBuilds();

    ColonyConfig.HyColony toCore() {
        return new ColonyConfig.HyColony(autosaveIntervalMinutes, builderInfiniteResources, creativeOperatorFreeBuilds);
    }
}
