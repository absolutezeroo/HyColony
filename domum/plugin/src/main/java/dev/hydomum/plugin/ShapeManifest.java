package dev.hydomum.plugin;

import com.hypixel.hytale.logger.HytaleLogger;
import com.hypixel.hytale.server.core.asset.type.blocktype.config.BlockType;
import dev.hydomum.api.OrnamentShape;
import dev.hydomum.api.ShapeCatalog;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.logging.Level;

/** The shape manifest that tools/domum generates (hydomum/shapes.json), read once the block assets are loaded. */
final class ShapeManifest {
    private static final HytaleLogger LOG = HytaleLogger.forEnclosingClass();
    private static final String SHAPES = "/hydomum/shapes.json";

    private ShapeManifest() {}

    /**
     * The shape manifest, without the shapes whose template block is not loaded (each logged); an unreadable entry or
     * an empty manifest is logged too.
     */
    static ShapeCatalog load() {
        try (InputStream in = ShapeManifest.class.getResourceAsStream(SHAPES)) {
            if (in == null) {
                throw new IllegalStateException("missing " + SHAPES);
            }
            ShapeCatalog catalog = ShapeCatalog.parse(new String(in.readAllBytes(), StandardCharsets.UTF_8));
            if (catalog.skipped() > 0 || catalog.all().isEmpty()) {
                // The generator and ShapeCatalog disagree on the manifest: shapes (and saved variants) go missing.
                LOG.at(Level.WARNING).log(
                        "hydomum: %s: %d shape(s) read, %d entry(ies) unreadable",
                        SHAPES, catalog.all().size(), catalog.skipped());
            }
            return catalog.retain(ShapeManifest::loaded);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    /** Whether shape's template block is loaded; logs a warning when not. */
    private static boolean loaded(OrnamentShape shape) {
        if (BlockType.getAssetMap().getAsset(shape.templateKey()) != null) {
            return true;
        }
        LOG.at(Level.WARNING).log(
                "hydomum: template %s not loaded, shape %s left out", shape.templateKey(), shape.id());
        return false;
    }
}
