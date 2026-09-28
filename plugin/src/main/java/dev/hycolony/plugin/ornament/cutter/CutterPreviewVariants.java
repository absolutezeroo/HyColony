package dev.hycolony.plugin.ornament.cutter;

import com.hypixel.hytale.logger.HytaleLogger;
import com.hypixel.hytale.server.core.asset.type.item.config.Item;
import com.hypixel.hytale.server.core.universe.world.World;
import dev.hycolony.core.ornament.VariantKey;
import dev.hycolony.plugin.ornament.registry.OrnamentVariantRegistry;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.logging.Level;

/**
 * Creates the variants the cutter shows as soon as the slots make them: the open group's shapes in the slots'
 * materials, in one batch (DO-1 grouped creation, a single asset rebuild), so that the preview and the shape buttons
 * show the variants' own icons (MC DO ArchitectsCutterScreen lists the outputs in the slots' materials) rather than
 * their templates'. The page is redrawn once they exist. Each variant is asked once per window. World thread.
 */
final class CutterPreviewVariants {
    private static final HytaleLogger LOG = HytaleLogger.forEnclosingClass();

    private final World world;
    private final OrnamentVariantRegistry registry;
    private final Runnable redraw;
    private final Set<String> asked = new HashSet<>();

    /** Asks registry for previewed variants, then runs redraw on world's thread. */
    CutterPreviewVariants(World world, OrnamentVariantRegistry registry, Runnable redraw) {
        this.world = world;
        this.registry = registry;
        this.redraw = redraw;
    }

    /**
     * Requests, in one batch (a single asset rebuild), those of keys that neither exist nor were asked already; the
     * page is redrawn once they exist. A failure leaves the template's icons.
     */
    void prepare(List<VariantKey> keys) {
        List<VariantKey> missing = keys.stream()
                .filter(k -> Item.getAssetMap().getAsset(k.blockTypeKey()) == null)
                .filter(k -> asked.add(k.blockTypeKey()))
                .toList();
        if (missing.isEmpty()) {
            return;
        }
        var _ = registry.request(missing).whenComplete((variants, error) -> {
            if (error != null) { // the craft asks again and reports it; here the template's icons just stay
                LOG.at(Level.FINE).withCause(error).log("hyornament: cutter previews not created");
                return;
            }
            try {
                world.execute(redraw);
            } catch (RuntimeException e) { // the world no longer takes tasks (stopping): nothing to redraw
                LOG.at(Level.FINE).log("hyornament: cutter preview redraw dropped");
            }
        });
    }
}
