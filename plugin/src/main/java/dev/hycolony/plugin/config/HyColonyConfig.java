package dev.hycolony.plugin.config;

import com.hypixel.hytale.codec.Codec;
import com.hypixel.hytale.codec.KeyedCodec;
import com.hypixel.hytale.codec.builder.BuilderCodec;
import dev.hycolony.core.kernel.config.ColonyConfig;
import dev.hycolony.core.kernel.config.FeatureFlags;
import java.util.function.BiConsumer;
import java.util.function.Supplier;

/**
 * mods/&lt;group&gt;_HyColony/config.json, in MineColonies' sections. A missing key or section keeps its default
 * (BuilderCodec fills only the keys present); values are clamped by the core's {@link ColonyConfig}.
 *
 * <p>The flat keys of the first config format are still read: each writes into its section, and its getter returns
 * null so it is never written back (BuilderField.encode skips null). The plugin saves the config at setup, so an old
 * flat file is rewritten in sections with its values on the first start.
 */
public final class HyColonyConfig {
    private static final BuilderCodec.Builder<HyColonyConfig> SECTIONS = BuilderCodec.builder(
                    HyColonyConfig.class, HyColonyConfig::new)
            .append(
                    new KeyedCodec<>("Gameplay", GameplaySection.CODEC),
                    (c, v) -> c.gameplay = orNew(v, GameplaySection::new),
                    c -> c.gameplay)
            .add()
            .append(
                    new KeyedCodec<>("Claims", ClaimsSection.CODEC),
                    (c, v) -> c.claims = orNew(v, ClaimsSection::new),
                    c -> c.claims)
            .add()
            .append(
                    new KeyedCodec<>("Permissions", PermissionsSection.CODEC),
                    (c, v) -> c.permissions = orNew(v, PermissionsSection::new),
                    c -> c.permissions)
            .add()
            .append(
                    new KeyedCodec<>("Commands", CommandsSection.CODEC),
                    (c, v) -> c.commands = orNew(v, CommandsSection::new),
                    c -> c.commands)
            .add()
            .append(
                    new KeyedCodec<>("Client", ClientSection.CODEC),
                    (c, v) -> c.client = orNew(v, ClientSection::new),
                    c -> c.client)
            .add()
            .append(
                    new KeyedCodec<>("HyColony", HyColonySection.CODEC),
                    (c, v) -> c.hycolony = orNew(v, HyColonySection::new),
                    c -> c.hycolony)
            .add()
            .append(
                    new KeyedCodec<>("Structurize", StructurizeSection.CODEC),
                    (c, v) -> c.structurize = orNew(v, StructurizeSection::new),
                    c -> c.structurize)
            .add();

    public static final BuilderCodec<HyColonyConfig> CODEC = legacy(SECTIONS).build();

    private GameplaySection gameplay = new GameplaySection();
    private ClaimsSection claims = new ClaimsSection();
    private PermissionsSection permissions = new PermissionsSection();
    private CommandsSection commands = new CommandsSection();
    private ClientSection client = new ClientSection();
    private HyColonySection hycolony = new HyColonySection();
    private StructurizeSection structurize = new StructurizeSection();

    /** The core configuration; the core clamps every value to MineColonies' bounds. */
    public ColonyConfig toCore() {
        return new ColonyConfig(
                gameplay.toCore(),
                claims.toCore(),
                permissions.toCore(),
                commands.toCore(),
                client.toCore(),
                hycolony.toCore(),
                structurize.toCore());
    }

    /** Which sub-plugins are switched on or off ({@code HyColony.SubPlugins}); a pack not named keeps its default. */
    public FeatureFlags subPlugins() {
        return hycolony.subPlugins();
    }

    /** The nine flat keys of the first format, read into their sections and never written back. */
    private static BuilderCodec.Builder<HyColonyConfig> legacy(BuilderCodec.Builder<HyColonyConfig> b) {
        readOnly(b, "InitialCitizenAmount", Codec.INTEGER, (c, v) -> c.gameplay.initialCitizenAmount = v);
        readOnly(b, "MaxCitizenPerColony", Codec.INTEGER, (c, v) -> c.gameplay.maxCitizenPerColony = v);
        readOnly(b, "InitialColonySize", Codec.INTEGER, (c, v) -> c.claims.initialColonySize = v);
        readOnly(b, "MinColonyDistance", Codec.INTEGER, (c, v) -> c.claims.minColonyDistance = v);
        readOnly(b, "MaxColonySize", Codec.INTEGER, (c, v) -> c.claims.maxColonySize = v);
        readOnly(b, "EnableColonyProtection", Codec.BOOLEAN, (c, v) -> c.permissions.enableColonyProtection = v);
        readOnly(b, "AutosaveIntervalMinutes", Codec.INTEGER, (c, v) -> c.hycolony.autosaveIntervalMinutes = v);
        readOnly(b, "BuilderInfiniteResources", Codec.BOOLEAN, (c, v) -> c.hycolony.builderInfiniteResources = v);
        readOnly(b, "CreativeOperatorFreeBuilds", Codec.BOOLEAN, (c, v) -> c.hycolony.creativeOperatorFreeBuilds = v);
        return b;
    }

    /** A key that is decoded (a null value is ignored) but never encoded. */
    private static <T> void readOnly(
            BuilderCodec.Builder<HyColonyConfig> b, String key, Codec<T> codec, BiConsumer<HyColonyConfig, T> set) {
        b.append(
                        new KeyedCodec<>(key, codec),
                        (c, v) -> {
                            if (v != null) {
                                set.accept(c, v);
                            }
                        },
                        c -> null)
                .add();
    }

    private static <S> S orNew(S section, Supplier<S> fresh) {
        return section != null ? section : fresh.get();
    }
}
