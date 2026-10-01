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
            .append(
                    new KeyedCodec<>("WorkersAlwaysWorkInRain", Codec.BOOLEAN),
                    (s, v) -> s.workersAlwaysWorkInRain = v,
                    s -> s.workersAlwaysWorkInRain)
            .add()
            .append(new KeyedCodec<>("FoodModifier", Codec.DOUBLE), (s, v) -> s.foodModifier = v, s -> s.foodModifier)
            .add()
            .build();

    private static final ColonyConfig.Gameplay DEFAULTS =
            ColonyConfig.defaults().gameplay();

    int initialCitizenAmount = DEFAULTS.initialCitizenAmount();
    int maxCitizenPerColony = DEFAULTS.maxCitizenPerColony();
    boolean workersAlwaysWorkInRain = DEFAULTS.workersAlwaysWorkInRain();
    double foodModifier = DEFAULTS.foodModifier();

    ColonyConfig.Gameplay toCore() {
        return new ColonyConfig.Gameplay(
                initialCitizenAmount, maxCitizenPerColony, workersAlwaysWorkInRain, foodModifier);
    }
}
