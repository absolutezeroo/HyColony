package dev.hyangler.plugin.world;

import com.hypixel.hytale.logger.HytaleLogger;
import com.hypixel.hytale.server.core.modules.time.WorldTimeResource;
import com.hypixel.hytale.server.core.universe.world.World;
import dev.hyangler.core.port.WorldClock;
import java.util.logging.Level;

/**
 * The world's hour, from its game time's time of day (as HytaleGameClock.dayTime; pre.5 has no getDayProgress), and
 * its moon phase (WorldTimeResource, fishing-hytale.md § 5.6).
 */
final class HytaleClock implements WorldClock {
    private static final HytaleLogger LOG = HytaleLogger.forEnclosingClass();
    private static final double NANOS_PER_HOUR = 3.6e12;
    /** The hour given when the time cannot be read: noon, when every day-only fish may bite. */
    private static final double FALLBACK_HOUR = 12.0;

    private final World world;
    private boolean warned;

    HytaleClock(World world) {
        this.world = world;
    }

    @Override
    public double hour() {
        try {
            return time().getGameDateTime().toLocalTime().toNanoOfDay() / NANOS_PER_HOUR;
        } catch (RuntimeException e) {
            failed(e, "time");
            return FALLBACK_HOUR;
        }
    }

    @Override
    public int moonPhase() {
        try {
            return time().getMoonPhase();
        } catch (RuntimeException e) {
            failed(e, "moon");
            return 0;
        }
    }

    private WorldTimeResource time() {
        return world.getEntityStore().getStore().getResource(WorldTimeResource.getResourceType());
    }

    private void failed(RuntimeException e, String what) {
        LOG.at(warned ? Level.FINE : Level.WARNING).withCause(e).log("HyAngler: %s read failed", what);
        warned = true;
    }
}
