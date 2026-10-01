package dev.hydomum.plugin;

import com.hypixel.hytale.server.core.Constants;
import com.hypixel.hytale.server.core.asset.LoadAssetEvent;
import com.hypixel.hytale.server.core.event.events.player.PlayerDisconnectEvent;
import com.hypixel.hytale.server.core.plugin.JavaPlugin;
import com.hypixel.hytale.server.core.plugin.JavaPluginInit;
import com.hypixel.hytale.server.core.universe.world.connectedblocks.ConnectedBlockRuleSet;
import com.hypixel.hytale.server.core.util.Config;
import dev.hyblockui.api.ConfigQuarantine;
import dev.hydomum.plugin.connect.HytaleFenceRules;
import dev.hydomum.plugin.connect.HytaleNeighbours;
import dev.hydomum.plugin.cutter.CutterGroupMemory;
import dev.hydomum.plugin.cutter.CutterSettings;
import dev.hydomum.plugin.cutter.CutterSystem;
import dev.hydomum.plugin.debug.OrnamentCommand;
import dev.hydomum.plugin.persistence.VariantStore;
import dev.hydomum.plugin.registry.OrnamentVariantRegistry;
import dev.hydomum.plugin.runtime.BlockTypeSynchronizer;
import dev.hydomum.plugin.runtime.MaterialCatalog;
import dev.hydomum.plugin.runtime.VariantAssets;
import dev.hydomum.plugin.runtime.VariantPalette;
import java.nio.file.Path;
import java.util.logging.Level;
import javax.annotation.Nonnull;

/**
 * HyDomum's entry point (MC DO's DomumOrnamentum mod class): the fence rule set type, the architect's cutter, the
 * /hydomum command, and once assets are loaded the shape and material catalogs, then the saved variants registered
 * again before any world loads a chunk holding one.
 */
public final class HyDomumPlugin extends JavaPlugin {
    private static final Path DATA = Constants.UNIVERSE_PATH.resolve("hydomum");

    private final Config<HyDomumConfig> config;

    public HyDomumPlugin(@Nonnull JavaPluginInit init) {
        super(init);
        // Before withConfig: preLoad decodes the file and a malformed one would abort the whole server start.
        ConfigQuarantine.moveAsideIfUnreadable(
                "HyDomum", getDataDirectory().resolve("config.json"), HyDomumConfig.CODEC);
        this.config = withConfig("config", HyDomumConfig.CODEC);
    }

    @Override
    protected void setup() {
        // save() returns a Future: log a failure instead of dropping it (CLAUDE.md sec 4).
        var _ = config.save().exceptionally(e -> {
            getLogger().at(Level.WARNING).withCause(e).log("HyDomum: could not save initial config");
            return null;
        });
        DomumIds ids = DomumIds.load();
        // Before LoadAssetEvent: our fences' and walls' BlockTypes name this rule set type.
        HytaleFenceRules.register(
                new HytaleNeighbours(ids.connections()), getCodecRegistry(ConnectedBlockRuleSet.CODEC));
        String pack = getIdentifier().toString();
        BlockTypeSynchronizer synchronizer = new BlockTypeSynchronizer(pack);
        VariantAssets assets = new VariantAssets(pack, DATA.resolve("assets"));
        OrnamentVariantRegistry ornaments = new OrnamentVariantRegistry(
                synchronizer,
                new VariantStore(DATA.resolve("variants.json")),
                assets,
                new VariantPalette(assets, synchronizer));
        // First and unconditionally: HyColony orders its protection before this system (HyDomumSystems), and a
        // SystemDependency on an unregistered class throws (ComponentRegistry.java:657-659).
        registerCutter(ids, ornaments);
        if (ids.ornamentTags().isEmpty()) {
            getLogger().at(Level.WARNING).log("HyDomum: no material tags, blocks disabled");
            return;
        }
        registerVariants(ids, ornaments);
    }

    /**
     * Registers the cutter's use system and forgets a player's last group on disconnect; without catalogs the cutter
     * opens nothing.
     */
    private void registerCutter(DomumIds ids, OrnamentVariantRegistry ornaments) {
        CutterGroupMemory groups = new CutterGroupMemory();
        getEntityStoreRegistry()
                .registerSystem(new CutterSystem(new CutterSettings(
                        ornaments,
                        groups,
                        Math.round(config.get().toCore().cutterCraftSeconds() * 1000),
                        ids.sound("cutter.open"),
                        ids.sound("cutter.close"))));
        getEventRegistry()
                .register(
                        PlayerDisconnectEvent.class,
                        e -> groups.forget(e.getPlayerRef().getUuid()));
    }

    /** Loads the catalogs and the saved variants once assets are loaded, and registers /hydomum. */
    private void registerVariants(DomumIds ids, OrnamentVariantRegistry ornaments) {
        // LOAD_LATE: after AssetModule has loaded the BlockType store (templates included); on the boot thread,
        // which holds no asset lock, before plugins start and worlds load chunks.
        getEventRegistry().register(LoadAssetEvent.PRIORITY_LOAD_LATE, LoadAssetEvent.class, e -> {
            try {
                ornaments.start(new OrnamentVariantRegistry.Catalogs(
                        ShapeManifest.load(), MaterialCatalog.load(ids.ornamentTags())));
            } catch (RuntimeException ex) {
                getLogger().at(Level.SEVERE).withCause(ex).log("HyDomum: could not load the variants");
            }
        });
        getCommandRegistry().registerCommand(new OrnamentCommand(ornaments));
    }
}
