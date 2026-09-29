package dev.hycolony.plugin.adapter;

import com.hypixel.hytale.server.core.modules.time.WorldTimeResource;
import com.hypixel.hytale.server.core.universe.world.World;
import dev.hycolony.core.kernel.port.GameClock;

/** Core tick counter (advanced by ColonyTickSystem) + Hytale day/night. */
public final class HytaleGameClock implements GameClock {
    /** WorldTimeResource's scaled time (a fraction of the day) at sunrise, 4:48, whatever the world's day length. */
    private static final double SUNRISE_SCALED_TIME = 0.25;

    /** WorldTimeResource's scaled time at sunset, 19:12. */
    private static final double SUNSET_SCALED_TIME = 0.75;

    private final World world;
    private long tick;

    public HytaleGameClock(World world) {
        this.world = world;
    }

    public void advance() {
        tick++;
    }

    @Override
    public long currentTick() {
        return tick;
    }

    /** MC world.isDay(): between Hytale's sunrise and sunset, both included. */
    @Override
    public boolean isDaytime() {
        WorldTimeResource time = world.getEntityStore().getStore().getResource(WorldTimeResource.getResourceType());
        return time.isScaledDayTimeWithinRange(SUNRISE_SCALED_TIME, SUNSET_SCALED_TIME);
    }
}
