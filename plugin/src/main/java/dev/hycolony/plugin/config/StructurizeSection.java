package dev.hycolony.plugin.config;

import com.hypixel.hytale.codec.Codec;
import com.hypixel.hytale.codec.KeyedCodec;
import com.hypixel.hytale.codec.builder.BuilderCodec;
import dev.hycolony.core.kernel.config.ColonyConfig;

/** The {@code Structurize} section of config.json (Structurize ServerConfiguration, used by the creative paste). */
final class StructurizeSection {
    static final BuilderCodec<StructurizeSection> CODEC = BuilderCodec.builder(
                    StructurizeSection.class, StructurizeSection::new)
            .append(
                    new KeyedCodec<>("MaxOperationsPerTick", Codec.INTEGER),
                    (s, v) -> s.maxOperationsPerTick = v,
                    s -> s.maxOperationsPerTick)
            .add()
            .build();

    int maxOperationsPerTick = ColonyConfig.defaults().structurize().maxOperationsPerTick();

    ColonyConfig.Structurize toCore() {
        return new ColonyConfig.Structurize(maxOperationsPerTick);
    }
}
