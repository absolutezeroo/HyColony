package dev.hycolony.plugin.config;

import com.hypixel.hytale.codec.Codec;
import com.hypixel.hytale.codec.KeyedCodec;
import com.hypixel.hytale.codec.builder.BuilderCodec;
import dev.hycolony.core.kernel.config.ColonyConfig;

/** The {@code Claims} section of config.json (MC ServerConfiguration claims). */
final class ClaimsSection {
    static final BuilderCodec<ClaimsSection> CODEC = BuilderCodec.builder(ClaimsSection.class, ClaimsSection::new)
            .append(
                    new KeyedCodec<>("MaxColonySize", Codec.INTEGER),
                    (s, v) -> s.maxColonySize = v,
                    s -> s.maxColonySize)
            .add()
            .append(
                    new KeyedCodec<>("MinColonyDistance", Codec.INTEGER),
                    (s, v) -> s.minColonyDistance = v,
                    s -> s.minColonyDistance)
            .add()
            .append(
                    new KeyedCodec<>("InitialColonySize", Codec.INTEGER),
                    (s, v) -> s.initialColonySize = v,
                    s -> s.initialColonySize)
            .add()
            .append(
                    new KeyedCodec<>("MaxDistanceFromWorldSpawn", Codec.INTEGER),
                    (s, v) -> s.maxDistanceFromWorldSpawn = v,
                    s -> s.maxDistanceFromWorldSpawn)
            .add()
            .append(
                    new KeyedCodec<>("MinDistanceFromWorldSpawn", Codec.INTEGER),
                    (s, v) -> s.minDistanceFromWorldSpawn = v,
                    s -> s.minDistanceFromWorldSpawn)
            .add()
            .build();

    private static final ColonyConfig.Claims DEFAULTS = ColonyConfig.defaults().claims();

    int maxColonySize = DEFAULTS.maxColonySize();
    int minColonyDistance = DEFAULTS.minColonyDistance();
    int initialColonySize = DEFAULTS.initialColonySize();
    int maxDistanceFromWorldSpawn = DEFAULTS.maxDistanceFromWorldSpawn();
    int minDistanceFromWorldSpawn = DEFAULTS.minDistanceFromWorldSpawn();

    ColonyConfig.Claims toCore() {
        return new ColonyConfig.Claims(
                maxColonySize,
                minColonyDistance,
                initialColonySize,
                maxDistanceFromWorldSpawn,
                minDistanceFromWorldSpawn);
    }
}
