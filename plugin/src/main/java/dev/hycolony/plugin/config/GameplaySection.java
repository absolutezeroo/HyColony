package dev.hycolony.plugin.config;

import com.hypixel.hytale.codec.Codec;
import com.hypixel.hytale.codec.KeyedCodec;
import com.hypixel.hytale.codec.builder.BuilderCodec;
import dev.hycolony.core.kernel.config.ColonyConfig;

/** The {@code Gameplay} section of config.json (MC ServerConfiguration gameplay). */
final class GameplaySection {
    static final BuilderCodec<GameplaySection> CODEC = BuilderCodec.builder(GameplaySection.class, GameplaySection::new)
            .append(
                    new KeyedCodec<>("InitialCitizenAmount", Codec.INTEGER),
                    (s, v) -> s.initialCitizenAmount = v,
                    s -> s.initialCitizenAmount)
            .add()
            .append(
                    new KeyedCodec<>("MaxCitizenPerColony", Codec.INTEGER),
                    (s, v) -> s.maxCitizenPerColony = v,
                    s -> s.maxCitizenPerColony)
            .add()
            .build();

    private static final ColonyConfig.Gameplay DEFAULTS =
            ColonyConfig.defaults().gameplay();

    int initialCitizenAmount = DEFAULTS.initialCitizenAmount();
    int maxCitizenPerColony = DEFAULTS.maxCitizenPerColony();

    ColonyConfig.Gameplay toCore() {
        return new ColonyConfig.Gameplay(initialCitizenAmount, maxCitizenPerColony);
    }
}
