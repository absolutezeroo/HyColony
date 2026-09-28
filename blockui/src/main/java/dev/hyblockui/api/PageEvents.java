package dev.hyblockui.api;

import com.hypixel.hytale.logger.HytaleLogger;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.logging.Level;

/**
 * Runs a window's event handler so that a failure never reaches Hytale's PageManager, which does not catch it
 * (CLAUDE.md § 4): the window stays open and answers the next click.
 */
public final class PageEvents {
    private static final HytaleLogger LOG = HytaleLogger.forEnclosingClass();
    /** Shared by every world thread, hence atomic. */
    private static final AtomicBoolean WARNED = new AtomicBoolean();

    private PageEvents() {}

    /** Runs {@code handler}; a RuntimeException is logged, SEVERE the first time and FINE after, then swallowed. */
    public static void guard(Class<?> page, Runnable handler) {
        try {
            handler.run();
        } catch (RuntimeException e) {
            LOG.at(WARNED.getAndSet(true) ? Level.FINE : Level.SEVERE).withCause(e).log(
                    "Window %s: an action failed", page.getSimpleName());
        }
    }
}
