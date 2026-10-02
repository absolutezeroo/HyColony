package dev.hycolony.core.citizen.wander;

import dev.hycolony.core.citizen.CitizenData;
import java.util.random.RandomGenerator;

/** When a citizen takes a leisure break (MC CitizenData.update, its leisure part). */
public final class LeisureTimer {
    private static final int TICKS_SECOND = 20;

    private LeisureTimer() {}

    /**
     * Counts {@code d}'s running break down by {@code elapsed} ticks, else starts one ({@link
     * CitizenData#LEISURE_TICKS}) with a chance of 1 in 1200 x (120 / home level) / {@code elapsed}: one break every
     * 120 / home level minutes on average. Falling asleep and waking up end a break ({@link CitizenData#setAsleep}).
     */
    public static void tick(CitizenData d, int elapsed, int homeLevel, RandomGenerator random) {
        if (d.leisureTime() > 0) {
            d.setLeisureTime(d.leisureTime() - elapsed);
            return;
        }
        // Deviation from MC: a home not built yet (level 0) counts as level 1, where MC's bound would overflow.
        int level = Math.max(1, homeLevel);
        if (random.nextInt(TICKS_SECOND * 60 * (int) (60 / (level / 2.0)) / elapsed) <= 0) {
            d.setLeisureTime(CitizenData.LEISURE_TICKS);
        }
    }
}
