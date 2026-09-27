package dev.hycolony.core.kernel.config;

import java.util.Map;

/**
 * The sub-plugin switches of config.json ({@code HyColony.SubPlugins}, pack name to boolean). Read tolerantly: a pack
 * the config does not name, or names with a value that is not a boolean, keeps its manifest's default.
 */
public record FeatureFlags(Map<String, ?> flags) {
    public FeatureFlags {
        flags = Map.copyOf(flags);
    }

    /** No switch at all: every pack keeps its manifest's default. */
    public static FeatureFlags none() {
        return new FeatureFlags(Map.of());
    }

    /** The pack's switch when it is a boolean, else {@code byDefault}. */
    public boolean enabled(String pack, boolean byDefault) {
        return flags.get(pack) instanceof Boolean on ? on : byDefault;
    }
}
