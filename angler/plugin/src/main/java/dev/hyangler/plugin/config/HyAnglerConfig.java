package dev.hyangler.plugin.config;

import com.hypixel.hytale.codec.KeyedCodec;
import com.hypixel.hytale.codec.builder.BuilderCodec;
import dev.hyangler.core.AnglerSettings;
import java.nio.file.Path;

/**
 * mods/&lt;group&gt;_HyAngler/config.json: one section, HyAngler (spec § 9). A missing key or section keeps its
 * default (BuilderCodec fills only the keys present); values are bounded by the core's {@link AnglerSettings}.
 */
public final class HyAnglerConfig {
    public static final BuilderCodec<HyAnglerConfig> CODEC = BuilderCodec.builder(
                    HyAnglerConfig.class, HyAnglerConfig::new)
            .append(
                    new KeyedCodec<>("HyAngler", AnglerSection.CODEC),
                    (c, v) -> c.angler = v == null ? new AnglerSection() : v,
                    c -> c.angler)
            .add()
            .build();

    private AnglerSection angler = new AnglerSection();

    /** The core's settings, bounded. */
    public AnglerSettings toCore() {
        return new AnglerSettings(
                        angler.biteTimeMultiplier, angler.rodWear, angler.maxLineDefault, angler.testCommandOpOnly)
                .bounded();
    }

    /**
     * Moves an unreadable config file aside before Hytale's preLoad decodes it, which would otherwise abort the whole
     * server start (AnglerConfigQuarantine); a missing file is left alone. Never throws.
     */
    public static void quarantine(Path file) {
        AnglerConfigQuarantine.moveAsideIfUnreadable("HyAngler", file, CODEC);
    }
}
