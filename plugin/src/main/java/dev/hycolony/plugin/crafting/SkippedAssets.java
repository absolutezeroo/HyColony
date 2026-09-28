package dev.hycolony.plugin.crafting;

import com.hypixel.hytale.logger.HytaleLogger;
import java.util.logging.Level;

/**
 * Logs an asset the recipe catalog could not read and leaves it out, so one bad entry (a sub-plugin's pack included)
 * costs only itself: a catalog that fell back to empty would drop every learnt recipe at the next colony load. The first
 * skip of a load is a WARNING, the others FINE.
 */
final class SkippedAssets {
    private static final HytaleLogger LOG = HytaleLogger.forEnclosingClass();

    private final String kind;
    private int count;

    SkippedAssets(String kind) {
        this.kind = kind;
    }

    /** Records that the {@code kind} asset {@code id} was left out because of {@code e}. */
    void skip(Object id, RuntimeException e) {
        LOG.at(count++ == 0 ? Level.WARNING : Level.FINE).withCause(e).log("Recipe catalog: %s %s left out", kind, id);
    }
}
