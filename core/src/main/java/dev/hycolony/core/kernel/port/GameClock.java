package dev.hycolony.core.kernel.port;

/**
 * Core time. {@code currentTick} advances 20 times per second while the world runs. The day time is MineColonies'
 * (24000 per day, 0 at dawn), mapped by phases on the world's day: its daytime, sunrise to sunset, is [0, 12600[ and
 * its night [12600, 24000[ (SP4 spec § 4).
 */
public interface GameClock {
    long currentTick();

    /** MC WorldUtil.isDayTime: day time at most NIGHT (12600), sunrise to sunset. */
    boolean isDaytime();

    /** MC's day time in [0, 24000[: 0 at sunrise, 12600 at sunset. */
    int dayTime();

    /**
     * The real ticks until the clock next reaches MC day time {@code dayTime}; {@link Long#MAX_VALUE} while the
     * world's time is paused.
     */
    long realTicksUntil(int dayTime);
}
