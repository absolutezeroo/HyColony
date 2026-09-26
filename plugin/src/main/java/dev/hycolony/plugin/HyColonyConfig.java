package dev.hycolony.plugin;

import com.hypixel.hytale.codec.Codec;
import com.hypixel.hytale.codec.KeyedCodec;
import com.hypixel.hytale.codec.builder.BuilderCodec;
import dev.hycolony.core.kernel.config.ColonyConfig;

/** mods/<group>_HyColony/config.json. Ranges are clamped like the MineColonies config. */
public final class HyColonyConfig {
    public static final BuilderCodec<HyColonyConfig> CODEC = BuilderCodec.builder(HyColonyConfig.class, HyColonyConfig::new)
            .append(new KeyedCodec<>("InitialCitizenAmount", Codec.INTEGER), (c, v) -> c.initialCitizenAmount = v, c -> c.initialCitizenAmount).add()
            .append(new KeyedCodec<>("MaxCitizenPerColony", Codec.INTEGER), (c, v) -> c.maxCitizenPerColony = v, c -> c.maxCitizenPerColony).add()
            .append(new KeyedCodec<>("InitialColonySize", Codec.INTEGER), (c, v) -> c.initialColonySize = v, c -> c.initialColonySize).add()
            .append(new KeyedCodec<>("MinColonyDistance", Codec.INTEGER), (c, v) -> c.minColonyDistance = v, c -> c.minColonyDistance).add()
            .append(new KeyedCodec<>("MaxColonySize", Codec.INTEGER), (c, v) -> c.maxColonySize = v, c -> c.maxColonySize).add()
            .append(new KeyedCodec<>("EnableColonyProtection", Codec.BOOLEAN), (c, v) -> c.enableColonyProtection = v, c -> c.enableColonyProtection).add()
            .append(new KeyedCodec<>("AutosaveIntervalMinutes", Codec.INTEGER), (c, v) -> c.autosaveIntervalMinutes = v, c -> c.autosaveIntervalMinutes).add()
            .append(new KeyedCodec<>("BuilderInfiniteResources", Codec.BOOLEAN), (c, v) -> c.builderInfiniteResources = v, c -> c.builderInfiniteResources).add()
            .append(new KeyedCodec<>("CreativeOperatorFreeBuilds", Codec.BOOLEAN), (c, v) -> c.creativeOperatorFreeBuilds = v, c -> c.creativeOperatorFreeBuilds).add()
            .build();

    private int initialCitizenAmount = 4;
    private int maxCitizenPerColony = 250;
    private int initialColonySize = 4;
    private int minColonyDistance = 8;
    private int maxColonySize = 20;
    private boolean enableColonyProtection = true;
    private int autosaveIntervalMinutes = 5;
    private boolean builderInfiniteResources = false;
    private boolean creativeOperatorFreeBuilds = true;

    public ColonyConfig toCore() {
        return new ColonyConfig(
                clamp(initialCitizenAmount, 1, 10),
                clamp(maxCitizenPerColony, 25, 500),
                clamp(initialColonySize, 1, 15),
                clamp(minColonyDistance, 1, 200),
                clamp(maxColonySize, 1, 250),
                enableColonyProtection,
                clamp(autosaveIntervalMinutes, 1, 60),
                builderInfiniteResources,
                creativeOperatorFreeBuilds);
    }

    private static int clamp(int v, int min, int max) {
        return Math.max(min, Math.min(max, v));
    }
}
