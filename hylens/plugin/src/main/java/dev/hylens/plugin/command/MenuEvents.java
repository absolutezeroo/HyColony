package dev.hylens.plugin.command;

import com.hypixel.hytale.logger.HytaleLogger;
import dev.hyblockui.api.PageEvents;
import java.util.logging.Level;

/**
 * Runs a HyLens menu's click as HyBlockUI's PageEvents.guard does, catching a LinkageError too: a click that comes
 * after HyLens stopped runs code whose classes are gone, and an Error would stop the world's thread. Each page makes
 * its own when it opens, so this class is loaded while HyLens runs.
 */
final class MenuEvents {
    private static final HytaleLogger LOG = HytaleLogger.forEnclosingClass();

    /** Runs {@code click} for the page {@code page}; a failure is logged, never thrown into Hytale's PageManager. */
    void guard(Class<?> page, Runnable click) {
        try {
            PageEvents.guard(page, click);
        } catch (LinkageError e) {
            LOG.at(Level.WARNING).withCause(e).log("HyLens: a click on its menu came after HyLens stopped");
        }
    }
}
