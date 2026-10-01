package dev.hycolony.plugin.adapter;

import com.hypixel.hytale.server.core.modules.time.WorldTimeResource;
import com.hypixel.hytale.server.core.universe.world.World;
import dev.hycolony.core.kernel.port.GameClock;

/**
 * Core tick counter (advanced by ColonyTickSystem) + Hytale day/night, mapped by phases on MC's day time: Hytale's
 * daytime (WorldTimeResource.SUNRISE_SECONDS for DAYTIME_SECONDS) is MC's [0, 12600[, its night the rest of MC's
 * 24000 (SP4 spec § 4). Each phase of the clock runs at a constant real speed (WorldTimeResource.tick), its world's
 * day and night durations.
 *
 * <p>Deviation from MC: while an administration command interpolates the time (startDayTimeInterpolation), the clock
 * runs faster and {@link #realTicksUntil} is wrong until it ends.
 */
public final class HytaleGameClock implements GameClock {
    /** WorldTimeResource's scaled time (a fraction of the day) at sunrise, 4:48, whatever the world's day length. */
    private static final double SUNRISE_SCALED_TIME = 0.25;

    /** WorldTimeResource's scaled time at sunset, 19:12. */
    private static final double SUNSET_SCALED_TIME = 0.75;

    /** MC's day time at nightfall (CitizenConstants.NIGHT), Hytale's sunset here. */
    private static final int MC_NIGHT = 12600;

    private static final int MC_DAY = 24000;

    private static final int TICKS_PER_SECOND = 20;

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
        return time().isScaledDayTimeWithinRange(SUNRISE_SCALED_TIME, SUNSET_SCALED_TIME);
    }

    /** The game time's second of the day placed on MC's day by phases. */
    @Override
    public int dayTime() {
        double s = time().getGameDateTime().toLocalTime().toNanoOfDay() / 1e9;
        double fromSunrise = s - WorldTimeResource.SUNRISE_SECONDS;
        int mc;
        if (fromSunrise >= 0 && fromSunrise < WorldTimeResource.DAYTIME_SECONDS) {
            mc = (int) (fromSunrise * MC_NIGHT / WorldTimeResource.DAYTIME_SECONDS);
        } else {
            double intoNight = Math.floorMod((long) Math.floor(fromSunrise - WorldTimeResource.DAYTIME_SECONDS), (long)
                    WorldTimeResource.SECONDS_PER_DAY);
            mc = MC_NIGHT + (int) (intoNight * (MC_DAY - MC_NIGHT) / WorldTimeResource.NIGHTTIME_SECONDS);
        }
        return Math.clamp(mc, 0, MC_DAY - 1);
    }

    /**
     * Real seconds from sunrise to each MC day time, at the world's day and night durations, as WorldTimeResource.tick
     * moves its clock; then the ticks between now and {@code target}, around the cycle.
     */
    @Override
    public long realTicksUntil(int target) {
        if (world.getWorldConfig().isGameTimePaused()) {
            return Long.MAX_VALUE;
        }
        double day = world.getDaytimeDurationSeconds();
        double night = world.getNighttimeDurationSeconds();
        double cycle = day + night;
        double ahead = realSeconds(target, day, night) - realSeconds(dayTime(), day, night);
        double seconds = ((ahead % cycle) + cycle) % cycle;
        return Math.round(seconds * TICKS_PER_SECOND);
    }

    /** Real seconds after sunrise at MC day time {@code mc}: day ticks at the day's pace, then the night's. */
    private static double realSeconds(int mc, double day, double night) {
        return mc < MC_NIGHT ? mc * day / MC_NIGHT : day + (mc - MC_NIGHT) * night / (MC_DAY - MC_NIGHT);
    }

    private WorldTimeResource time() {
        return world.getEntityStore().getStore().getResource(WorldTimeResource.getResourceType());
    }
}
