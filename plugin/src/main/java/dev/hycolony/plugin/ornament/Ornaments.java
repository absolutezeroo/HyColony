package dev.hycolony.plugin.ornament;

import com.hypixel.hytale.server.core.Constants;
import com.hypixel.hytale.server.core.asset.LoadAssetEvent;
import com.hypixel.hytale.server.core.plugin.JavaPlugin;
import dev.hycolony.plugin.ornament.debug.OrnamentCommand;
import dev.hycolony.plugin.ornament.persistence.VariantStore;
import dev.hycolony.plugin.ornament.registry.OrnamentVariantRegistry;
import dev.hycolony.plugin.ornament.runtime.BlockTypeSynchronizer;
import dev.hycolony.plugin.ornament.runtime.VariantAssets;
import java.nio.file.Path;
import java.util.logging.Level;

/**
 * Wires the runtime Domum Ornamentum variants (experiment, docs/research/domum-ornamentum.md B.11): the /hyornament
 * command, and the saved variants registered again once assets are loaded, before any world loads a chunk holding one.
 */
public final class Ornaments {
    private static final Path HYCOLONY_DIR = Constants.UNIVERSE_PATH.resolve("hycolony");

    private Ornaments() {}

    /** Registers the command and the boot-time restore on {@code plugin}; call it from {@code setup}. */
    public static void register(JavaPlugin plugin) {
        OrnamentVariantRegistry ornaments = new OrnamentVariantRegistry(
                new BlockTypeSynchronizer(plugin.getIdentifier().toString()),
                new VariantStore(HYCOLONY_DIR.resolve("ornament-variants.json")),
                new VariantAssets(plugin.getIdentifier().toString(), HYCOLONY_DIR.resolve("ornament-assets")));
        // LOAD_LATE: after AssetModule has loaded the BlockType store (templates included); on the boot thread,
        // which holds no asset lock, before plugins start and worlds load chunks.
        plugin.getEventRegistry().register(LoadAssetEvent.PRIORITY_LOAD_LATE, LoadAssetEvent.class, e -> {
            try {
                ornaments.restoreSaved();
            } catch (RuntimeException ex) {
                plugin.getLogger().at(Level.SEVERE).withCause(ex).log("HyColony: could not restore ornament variants");
            }
        });
        plugin.getCommandRegistry().registerCommand(new OrnamentCommand(ornaments));
    }
}
