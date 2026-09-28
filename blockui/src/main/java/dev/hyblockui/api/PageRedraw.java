package dev.hyblockui.api;

import com.hypixel.hytale.logger.HytaleLogger;
import com.hypixel.hytale.server.core.universe.world.World;
import java.util.function.BooleanSupplier;
import java.util.logging.Level;

/**
 * Redraws a page once, later on its world's thread, however many changes come first: a craft or a drop changes
 * several containers, each reporting on its own. Nothing is scheduled once the page no longer wants it. World thread.
 */
public final class PageRedraw {
    private static final HytaleLogger LOG = HytaleLogger.forEnclosingClass();

    private final World world;
    private final Runnable redraw;
    private final BooleanSupplier wanted;
    private boolean pending;

    /** A redraw of a page in world that runs redraw, while wanted holds. */
    public PageRedraw(World world, Runnable redraw, BooleanSupplier wanted) {
        this.world = world;
        this.redraw = redraw;
        this.wanted = wanted;
    }

    /** Schedules the redraw unless one is pending or the page no longer wants it. A failing redraw is logged. */
    public void soon() {
        if (pending || !wanted.getAsBoolean()) {
            return;
        }
        pending = true;
        try {
            world.execute(this::run);
        } catch (RuntimeException e) { // the world no longer takes tasks (stopping): nothing to redraw
            pending = false;
        }
    }

    private void run() {
        pending = false;
        try {
            if (wanted.getAsBoolean()) {
                redraw.run();
            }
        } catch (RuntimeException e) {
            LOG.at(Level.SEVERE).withCause(e).log("hyblockui: a page redraw failed");
        }
    }
}
