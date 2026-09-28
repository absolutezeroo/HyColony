package dev.hycolony.plugin.ornament;

import com.hypixel.hytale.logger.HytaleLogger;
import com.hypixel.hytale.server.core.Constants;
import com.hypixel.hytale.server.core.asset.LoadAssetEvent;
import com.hypixel.hytale.server.core.asset.type.blocktype.config.BlockType;
import com.hypixel.hytale.server.core.plugin.JavaPlugin;
import dev.hycolony.core.ornament.OrnamentShape;
import dev.hycolony.core.ornament.ShapeCatalog;
import dev.hycolony.plugin.IdMap;
import dev.hycolony.plugin.ornament.debug.OrnamentCommand;
import dev.hycolony.plugin.ornament.persistence.VariantStore;
import dev.hycolony.plugin.ornament.registry.OrnamentVariantRegistry;
import dev.hycolony.plugin.ornament.runtime.BlockTypeSynchronizer;
import dev.hycolony.plugin.ornament.runtime.MaterialCatalog;
import dev.hycolony.plugin.ornament.runtime.VariantAssets;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.logging.Level;

/**
 * Wires Domum Ornamentum's runtime variants: the /hyornament command, and once assets are loaded the shape and
 * material catalogs, then the saved variants registered again before any world loads a chunk holding one. Nothing
 * is wired when the DO pack is off (no material tag in the id-map).
 */
public final class Ornaments {
    private static final HytaleLogger LOG = HytaleLogger.forEnclosingClass();
    private static final Path HYCOLONY_DIR = Constants.UNIVERSE_PATH.resolve("hycolony");
    private static final String SHAPES = "/hycolony/ornament/shapes.json";

    private Ornaments() {}

    /** Registers the command and the boot-time load on {@code plugin}; call it from {@code setup}. */
    public static void register(JavaPlugin plugin, IdMap ids) {
        if (ids.ornamentTags().isEmpty()) {
            return;
        }
        OrnamentVariantRegistry ornaments = new OrnamentVariantRegistry(
                new BlockTypeSynchronizer(plugin.getIdentifier().toString()),
                new VariantStore(HYCOLONY_DIR.resolve("ornament-variants.json")),
                new VariantAssets(plugin.getIdentifier().toString(), HYCOLONY_DIR.resolve("ornament-assets")));
        // LOAD_LATE: after AssetModule has loaded the BlockType store (templates included); on the boot thread,
        // which holds no asset lock, before plugins start and worlds load chunks.
        plugin.getEventRegistry().register(LoadAssetEvent.PRIORITY_LOAD_LATE, LoadAssetEvent.class, e -> {
            try {
                ornaments.start(
                        new OrnamentVariantRegistry.Catalogs(shapes(), MaterialCatalog.load(ids.ornamentTags())));
            } catch (RuntimeException ex) {
                LOG.at(Level.SEVERE).withCause(ex).log("HyColony: could not load Domum Ornamentum variants");
            }
        });
        plugin.getCommandRegistry().registerCommand(new OrnamentCommand(ornaments));
    }

    /** The shape manifest, without the shapes whose template block is not loaded (each logged). */
    private static ShapeCatalog shapes() {
        try (InputStream in = Ornaments.class.getResourceAsStream(SHAPES)) {
            if (in == null) {
                throw new IllegalStateException("missing " + SHAPES);
            }
            return ShapeCatalog.parse(new String(in.readAllBytes(), StandardCharsets.UTF_8))
                    .retain(Ornaments::loaded);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private static boolean loaded(OrnamentShape shape) {
        if (BlockType.getAssetMap().getAsset(shape.templateKey()) != null) {
            return true;
        }
        LOG.at(Level.WARNING).log(
                "hyornament: template %s not loaded, shape %s left out", shape.templateKey(), shape.id());
        return false;
    }
}
