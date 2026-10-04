package dev.hyangler.core.cast;

import dev.hyangler.api.BiteTimes;
import java.util.random.RandomGenerator;

/**
 * Vanilla's bite delays (FishingHook.catchingFish; wiki: Fishing, spec § 5): wait U[100, 600] − 100 × lure, approach
 * U[20, 80], window U[20, 40]; rain speeds the count, a hidden sky slows it.
 */
public final class BiteTimer {
    static final int MIN_WAIT = 100;
    static final int MAX_WAIT = 600;
    static final int LURE_TICKS = 100;
    static final int MIN_APPROACH = 20;
    static final int MAX_APPROACH = 80;
    static final int MIN_WINDOW = 20;
    static final int MAX_WINDOW = 40;
    /** Vanilla: in rain, one tick in four counts double. */
    static final double RAIN_CHANCE = 0.25;
    /** Vanilla: under a hidden sky, one tick in two does not count. */
    static final double NO_SKY_CHANCE = 0.5;

    private BiteTimer() {}

    /** The three delays; the wait scaled by the config's multiplier, at least 1 tick. */
    public static BiteTimes roll(int lure, double multiplier, RandomGenerator rng) {
        int wait = rng.nextInt(MIN_WAIT, MAX_WAIT + 1) - LURE_TICKS * lure;
        int scaled = Math.max(1, (int) Math.round(wait * multiplier));
        return new BiteTimes(
                scaled, rng.nextInt(MIN_APPROACH, MAX_APPROACH + 1), rng.nextInt(MIN_WINDOW, MAX_WINDOW + 1));
    }

    /** How much one tick in water counts: 1, +1 at 25 % in rain, −1 at 50 % under a hidden sky (both drawn). */
    static int step(boolean raining, boolean skyVisible, RandomGenerator rng) {
        int step = 1;
        if (rng.nextDouble() < RAIN_CHANCE && raining) {
            step++;
        }
        if (rng.nextDouble() < NO_SKY_CHANCE && !skyVisible) {
            step--;
        }
        return step;
    }
}
