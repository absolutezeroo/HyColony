package dev.hycolony.core.testing;

import dev.hycolony.core.kernel.port.GameClock;

/** A clock tests set by hand; one MC day tick lasts {@link #realTicksPerDayTick} real ticks. */
public final class FakeClock implements GameClock {
    private static final int DAY = 24000;

    public long tick;
    public boolean daytime = true;
    public int dayTime;
    public boolean paused;
    public double realTicksPerDayTick = 1;

    @Override
    public long currentTick() {
        return tick;
    }

    @Override
    public boolean isDaytime() {
        return daytime;
    }

    @Override
    public int dayTime() {
        return dayTime;
    }

    @Override
    public long realTicksUntil(int target) {
        return paused ? Long.MAX_VALUE : Math.round(Math.floorMod(target - dayTime, DAY) * realTicksPerDayTick);
    }
}
