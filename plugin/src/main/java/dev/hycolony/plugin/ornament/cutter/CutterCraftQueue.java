package dev.hycolony.plugin.ornament.cutter;

import com.hypixel.hytale.logger.HytaleLogger;
import com.hypixel.hytale.server.core.universe.world.World;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import java.util.function.DoubleConsumer;
import java.util.logging.Level;

/**
 * Crafts one at a time at the cutter, each taking the configured time while a progress bar fills, as Hytale's benches
 * queue their crafts (CraftingManager.queueCraft). A craft that fails, or the window closing, ends the queue.
 *
 * <p>Deviation from MC: DO's cutter crafts at once; this runs only when HyColony.CutterCraftSeconds is above 0.
 * World thread.
 */
final class CutterCraftQueue {
    /**
     * How often the progress bar moves, in milliseconds: 10 moves a second read as a smooth fill (a custom ProgressBar
     * has no client-side animation, unlike a bench's own bar). The trade-off: the page's clicks (tabs, drops) are
     * dropped while an update awaits the client's acknowledgement (PageManager.handleEvent), about ping / TICK_MILLIS of
     * them during a craft; shorter would lose nearly all of them on a distant server.
     */
    static final long TICK_MILLIS = 100;

    private static final HytaleLogger LOG = HytaleLogger.forEnclosingClass();

    private final World world;
    private final long craftMillis;
    private final BooleanSupplier open;
    private final DoubleConsumer progress;
    private Consumer<Consumer<Boolean>> craftOne = done -> done.accept(false);
    private int remaining;
    private long startedAt;
    private int generation;
    private double shown;

    /** A queue in world whose crafts take craftMillis each while open holds, showing 0-1 through progress. */
    CutterCraftQueue(World world, long craftMillis, BooleanSupplier open, DoubleConsumer progress) {
        this.world = world;
        this.craftMillis = craftMillis;
        this.open = open;
        this.progress = progress;
    }

    /** The craft under way's progress, 0-1: a redrawn page shows it again. */
    double progress() {
        return shown;
    }

    /** Whether crafts are queued: the craft buttons wait meanwhile. */
    boolean busy() {
        return remaining > 0;
    }

    /**
     * Crafts crafts times, one per craftMillis: each runs craftOne, which reports whether it crafted. Nothing starts
     * while busy.
     */
    void start(int crafts, Consumer<Consumer<Boolean>> craft) {
        if (busy() || crafts <= 0) {
            return;
        }
        this.craftOne = craft;
        this.remaining = crafts;
        next();
    }

    /** Stops the queue; the craft under way is dropped before it takes anything. */
    void cancel() {
        remaining = 0;
        generation++;
        show(0);
    }

    private void next() {
        startedAt = System.currentTimeMillis();
        schedule(++generation);
    }

    private void schedule(int scheduled) {
        CompletableFuture.delayedExecutor(TICK_MILLIS, TimeUnit.MILLISECONDS).execute(() -> {
            try {
                world.execute(() -> tick(scheduled));
            } catch (RuntimeException e) { // the world no longer takes tasks (stopping): the queue just ends
                LOG.at(Level.FINE).log("hyornament: cutter craft queue dropped");
            }
        });
    }

    /** Moves the bar; once the craft's time is up, crafts and goes on to the next, or ends. */
    private void tick(int scheduled) {
        if (scheduled != generation || remaining <= 0) {
            return;
        }
        if (!open.getAsBoolean()) {
            cancel();
            return;
        }
        double done = Math.min(1.0, (System.currentTimeMillis() - startedAt) / (double) craftMillis);
        show(done);
        if (done < 1.0) {
            schedule(scheduled);
            return;
        }
        craftOne.accept(crafted -> {
            if (scheduled != generation) {
                return; // cancelled meanwhile
            }
            remaining = crafted ? remaining - 1 : 0;
            if (remaining > 0) {
                next();
            } else {
                show(0);
            }
        });
    }

    private void show(double value) {
        shown = value;
        progress.accept(value);
    }
}
