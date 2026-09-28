package dev.hydomum.plugin.cutter;

import com.hypixel.hytale.logger.HytaleLogger;
import com.hypixel.hytale.server.core.asset.type.item.config.Item;
import com.hypixel.hytale.server.core.universe.world.World;
import dev.hydomum.api.VariantKey;
import dev.hydomum.plugin.registry.OrnamentVariantRegistry;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.function.BooleanSupplier;
import java.util.logging.Level;

/**
 * Creates the variants the cutter shows as soon as the slots make them: the open group's shapes in the slots'
 * materials, in one batch (DO-1 grouped creation, a single asset rebuild), so that the preview and the shape buttons
 * show the variants' own icons (MC DO ArchitectsCutterScreen lists the outputs in the slots' materials) rather than
 * their templates'. The request waits until the slots have not changed for {@link #DELAY_MILLIS}, so that trying
 * materials creates nothing; the page is redrawn once the batch ends. Each variant is asked once per window. World
 * thread.
 */
final class CutterPreviewVariants {
    /** How long the slots must stay unchanged before their variants are created, in milliseconds. */
    static final long DELAY_MILLIS = 1000;

    private static final HytaleLogger LOG = HytaleLogger.forEnclosingClass();

    private final World world;
    private final OrnamentVariantRegistry registry;
    private final Runnable redraw;
    private final BooleanSupplier open;
    private final Set<String> asked = new HashSet<>();
    private List<VariantKey> waiting = List.of();
    private int generation;
    private int inFlight;

    /** Asks registry for previewed variants while open holds, then runs redraw on world's thread. */
    CutterPreviewVariants(World world, OrnamentVariantRegistry registry, Runnable redraw, BooleanSupplier open) {
        this.world = world;
        this.registry = registry;
        this.redraw = redraw;
        this.open = open;
    }

    /**
     * Schedules the request of those of keys that neither exist nor were asked already, unless the same ones are
     * waiting: a redraw does not restart the delay, other slots do.
     */
    void prepare(List<VariantKey> keys) {
        List<VariantKey> missing = keys.stream()
                .filter(k -> Item.getAssetMap().getAsset(k.blockTypeKey()) == null)
                .filter(k -> !asked.contains(k.blockTypeKey()))
                .toList();
        if (missing.equals(waiting)) {
            return;
        }
        waiting = missing;
        int scheduled = ++generation;
        if (!missing.isEmpty()) {
            CompletableFuture.delayedExecutor(DELAY_MILLIS, TimeUnit.MILLISECONDS)
                    .execute(() -> onWorld(() -> request(scheduled)));
        }
    }

    /** Whether previews are waiting for the delay or being created: the page shows a spinner meanwhile. */
    boolean preparing() {
        return !waiting.isEmpty() || inFlight > 0;
    }

    /**
     * Asks for the waiting keys in one batch, unless other slots came since they were scheduled or the window closed
     * meanwhile (its materials went back: nothing was chosen).
     */
    private void request(int scheduled) {
        if (scheduled != generation || waiting.isEmpty() || !open.getAsBoolean()) {
            return;
        }
        List<VariantKey> batch = waiting;
        waiting = List.of();
        batch.forEach(k -> asked.add(k.blockTypeKey()));
        inFlight++;
        var _ = registry.request(batch).whenComplete((variants, error) -> {
            if (error != null) { // the craft asks again and reports it; the batch's others may exist: redraw anyway
                LOG.at(Level.FINE).withCause(error).log("hydomum: cutter previews not all created");
            }
            onWorld(() -> {
                inFlight--;
                redraw.run();
            });
        });
    }

    /** Runs task on the world thread; a stopping world drops it. */
    private void onWorld(Runnable task) {
        try {
            world.execute(task);
        } catch (RuntimeException e) { // the world no longer takes tasks (stopping): nothing to show
            LOG.at(Level.FINE).log("hydomum: cutter preview task dropped");
        }
    }
}
