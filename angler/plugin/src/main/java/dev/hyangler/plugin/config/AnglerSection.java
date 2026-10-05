package dev.hyangler.plugin.config;

import com.hypixel.hytale.codec.Codec;
import com.hypixel.hytale.codec.KeyedCodec;
import com.hypixel.hytale.codec.builder.BuilderCodec;

/** The HyAngler section of config.json (spec § 9): read as written, bounded by the core (AnglerSettings.bounded). */
final class AnglerSection {
    static final BuilderCodec<AnglerSection> CODEC = BuilderCodec.builder(AnglerSection.class, AnglerSection::new)
            .append(
                    new KeyedCodec<>("BiteTimeMultiplier", Codec.DOUBLE),
                    (s, v) -> s.biteTimeMultiplier = v,
                    s -> s.biteTimeMultiplier)
            .add()
            .append(new KeyedCodec<>("RodWear", Codec.BOOLEAN), (s, v) -> s.rodWear = v, s -> s.rodWear)
            .add()
            .append(
                    new KeyedCodec<>("MaxLineDefault", Codec.INTEGER),
                    (s, v) -> s.maxLineDefault = v,
                    s -> s.maxLineDefault)
            .add()
            .append(
                    new KeyedCodec<>("TestCommandOpOnly", Codec.BOOLEAN),
                    (s, v) -> s.testCommandOpOnly = v,
                    s -> s.testCommandOpOnly)
            .add()
            .build();

    double biteTimeMultiplier = 1.0;
    boolean rodWear = true;
    int maxLineDefault = 32;
    boolean testCommandOpOnly = true;
}
