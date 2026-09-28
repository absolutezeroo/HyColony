package dev.hydomum.plugin;

import com.hypixel.hytale.codec.Codec;
import com.hypixel.hytale.codec.KeyedCodec;
import com.hypixel.hytale.codec.builder.BuilderCodec;
import dev.hydomum.core.DomumConfig;
import org.jspecify.annotations.Nullable;

/**
 * mods/HyColony_hydomum/config.json: our own options in a HyDomum section (MC DO has no config). A missing key or
 * section keeps its default (BuilderCodec fills only the keys present); values are clamped by {@link DomumConfig}.
 */
final class HyDomumConfig {
    /** The HyDomum section. */
    static final class Section {
        static final BuilderCodec<Section> CODEC = BuilderCodec.builder(Section.class, Section::new)
                .append(
                        new KeyedCodec<>("CutterCraftSeconds", Codec.DOUBLE),
                        (s, v) -> s.cutterCraftSeconds = v,
                        s -> s.cutterCraftSeconds)
                .add()
                .build();

        double cutterCraftSeconds = DomumConfig.defaults().cutterCraftSeconds();
    }

    static final BuilderCodec<HyDomumConfig> CODEC = BuilderCodec.builder(HyDomumConfig.class, HyDomumConfig::new)
            .append(new KeyedCodec<>("HyDomum", Section.CODEC), (c, v) -> c.section = orNew(v), c -> c.section)
            .add()
            .build();

    private Section section = new Section();

    /** The core's clamped settings. */
    DomumConfig toCore() {
        return new DomumConfig(section.cutterCraftSeconds);
    }

    private static Section orNew(@Nullable Section read) {
        return read == null ? new Section() : read;
    }
}
