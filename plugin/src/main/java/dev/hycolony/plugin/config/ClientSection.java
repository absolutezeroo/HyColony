package dev.hycolony.plugin.config;

import com.hypixel.hytale.codec.Codec;
import com.hypixel.hytale.codec.KeyedCodec;
import com.hypixel.hytale.codec.builder.BuilderCodec;
import dev.hycolony.core.kernel.config.ColonyConfig;

/** The {@code Client} section of config.json (MC ClientConfiguration gameplay, applied server-side). */
final class ClientSection {
    static final BuilderCodec<ClientSection> CODEC = BuilderCodec.builder(ClientSection.class, ClientSection::new)
            .append(
                    new KeyedCodec<>("BuildGoggleRange", Codec.INTEGER),
                    (s, v) -> s.buildGoggleRange = v,
                    s -> s.buildGoggleRange)
            .add()
            .append(
                    new KeyedCodec<>("ColonyTeamBorders", Codec.BOOLEAN),
                    (s, v) -> s.colonyTeamBorders = v,
                    s -> s.colonyTeamBorders)
            .add()
            .build();

    int buildGoggleRange = ColonyConfig.defaults().client().buildGoggleRange();
    boolean colonyTeamBorders = ColonyConfig.defaults().client().colonyTeamBorders();

    ColonyConfig.Client toCore() {
        return new ColonyConfig.Client(buildGoggleRange, colonyTeamBorders);
    }
}
