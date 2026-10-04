package dev.hycolony.plugin.config;

import com.hypixel.hytale.codec.Codec;
import com.hypixel.hytale.codec.KeyedCodec;
import com.hypixel.hytale.codec.builder.BuilderCodec;
import dev.hycolony.core.kernel.config.ColonyConfig;

/** The {@code Combat} section of config.json (MC ServerConfiguration, section combat). */
final class CombatSection {
    static final BuilderCodec<CombatSection> CODEC = BuilderCodec.builder(CombatSection.class, CombatSection::new)
            .append(
                    new KeyedCodec<>("MobAttackCitizens", Codec.BOOLEAN),
                    (s, v) -> s.mobAttackCitizens = v,
                    s -> s.mobAttackCitizens)
            .add()
            .build();

    boolean mobAttackCitizens = ColonyConfig.defaults().combat().mobAttackCitizens();

    ColonyConfig.Combat toCore() {
        return new ColonyConfig.Combat(mobAttackCitizens);
    }
}
