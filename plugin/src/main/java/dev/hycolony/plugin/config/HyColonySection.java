package dev.hycolony.plugin.config;

import com.hypixel.hytale.codec.Codec;
import com.hypixel.hytale.codec.KeyedCodec;
import com.hypixel.hytale.codec.builder.BuilderCodec;
import dev.hycolony.core.kernel.config.ColonyConfig;
import dev.hycolony.core.kernel.config.FeatureFlags;
import java.util.LinkedHashMap;
import java.util.Map;
import org.bson.BsonDocument;

/** The {@code HyColony} section of config.json: our own options, none of them a MineColonies option. */
final class HyColonySection {
    // BSON_DOCUMENT is deprecated, but a Map codec of booleans would reject the whole file for one bad flag.
    @SuppressWarnings("deprecation")
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
            .append(new KeyedCodec<>("SubPlugins", Codec.BSON_DOCUMENT), (s, v) -> s.subPlugins = v, s -> s.subPlugins)
            .add()
            .build();

    private static final ColonyConfig.HyColony DEFAULTS =
            ColonyConfig.defaults().hycolony();

    int autosaveIntervalMinutes = DEFAULTS.autosaveIntervalMinutes();
    boolean builderInfiniteResources = DEFAULTS.builderInfiniteResources();
    boolean creativeOperatorFreeBuilds = DEFAULTS.creativeOperatorFreeBuilds();
    /** Sub-plugin name -> enabled; kept as read (unknown names and bad values included) so a save never loses one. */
    BsonDocument subPlugins = new BsonDocument();

    ColonyConfig.HyColony toCore() {
        return new ColonyConfig.HyColony(autosaveIntervalMinutes, builderInfiniteResources, creativeOperatorFreeBuilds);
    }

    /**
     * The {@code SubPlugins} switches; a value that is not a boolean is passed as is, so the pack keeps its default.
     */
    FeatureFlags subPlugins() {
        Map<String, Object> flags = new LinkedHashMap<>();
        if (subPlugins != null) {
            subPlugins.forEach(
                    (name, v) -> flags.put(name, v.isBoolean() ? v.asBoolean().getValue() : v));
        }
        return new FeatureFlags(flags);
    }
}
