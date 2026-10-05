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
    /**
     * Deviation from vanilla: a lure above this counts as this. From Lure 6 every vanilla wait is 0 or less and vanilla
     * never bites (Paper's patch "Fix Lure infinite loop", paper-server FishingHook.java.patch, fixes the same loop); a
     * rod of another mod must still catch.
     */
    static final int MAX_LURE = 5;

    static final int MIN_APPROACH = 20;
    static final int MAX_APPROACH = 80;
    static final int MIN_WINDOW = 20;
    static final int MAX_WINDOW = 40;
    /** Vanilla: in rain, one tick in four counts double. */
    static final double RAIN_CHANCE = 0.25;
    /** Vanilla: under a hidden sky, one tick in two does not count. */
    static final double NO_SKY_CHANCE = 0.5;

    private BiteTimer() {}

    /** The three delays; the wait scaled by the config's multiplier, at least 1 tick; a lure above 5 counts as 5. */
    public static BiteTimes roll(int lure, double multiplier, RandomGenerator rng) {
        int cut = LURE_TICKS * Math.clamp(lure, 0, MAX_LURE); // vanilla: lureSpeed = Math.max(0, lureSpeed)
        // Deviation from vanilla: it draws again, on the next tick, a wait of 0 or less (40 % of draws at Lure 3).
        // Drawing from the positive part keeps the wait's distribution and drops those few ticks.
        int wait = rng.nextInt(Math.max(MIN_WAIT, cut + 1), MAX_WAIT + 1) - cut;
        int scaled = Math.max(1, (int) Math.round(wait * multiplier));
        return new BiteTimes(
                scaled, rng.nextInt(MIN_APPROACH, MAX_APPROACH + 1), rng.nextInt(MIN_WINDOW, MAX_WINDOW + 1));
    }

    /**
     * The chance a waiting fish teases the bait this tick, for the wait's ticks left (vanilla catchingFish's
     * teaseChance): 0.15, raised by 0.01, 0.02 then 0.05 a tick over the last 60, 40 and 20 ticks.
     */
    public static double teaseChance(int left) {
        double chance = 0.15;
        if (left < 20) {
            chance += (20 - left) * 0.05;
        } else if (left < 40) {
            chance += (40 - left) * 0.02;
        } else if (left < 60) {
            chance += (60 - left) * 0.01;
        }
        return chance;
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
