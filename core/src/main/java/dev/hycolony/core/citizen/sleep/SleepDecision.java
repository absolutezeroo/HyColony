package dev.hycolony.core.citizen.sleep;

import dev.hycolony.core.kernel.BlockPos;
import dev.hycolony.core.kernel.port.GameClock;
import java.util.Optional;

/**
 * When a citizen goes to bed and gets up: the sleep part of MC CitizenAI.calculateNextState, with
 * CitizenSleepHandler.shouldGoSleep. Not ported (no such system): the miner's underground distance, the WORK_LONGER
 * research, the sick citizen kept in bed, and the "hometoofar" complaint (an interaction, at more than
 * MAX_NO_COMPLAIN_DISTANCE = 160 blocks from work), deferred with citizen interactions (SP4 spec § 5.1).
 */
public final class SleepDecision {
    /** MC CitizenConstants.NIGHT: in bed by this day time, sunset here. */
    public static final int NIGHT = 12600;
    /** MC calculateNextState: no bedtime before NIGHT - 2000 (WorldUtil.isPastTime). */
    public static final int EVENING = NIGHT - 2000;
    /** MC calculateNextState: asleep, the decision runs again only after 15 s (setCurrentDelay(20 * 15)). */
    public static final int SLEEP_DECIDE_DELAY_TICKS = 20 * 15;
    /** MC CitizenSleepHandler.Y_DIFF_WEIGHT: a block of height weighs one and a half of distance. */
    static final double Y_DIFF_WEIGHT = 1.5;
    /** MC CitizenSleepHandler.TIME_PER_BLOCK: ticks of walk per block. */
    static final int TIME_PER_BLOCK = 6;

    /** What the citizen's sleep does at this decision. */
    public enum Verdict {
        NONE,
        GO_TO_SLEEP,
        STAY_ASLEEP,
        WAKE_UP
    }

    private SleepDecision() {}

    /**
     * MC calculateNextState: in the evening and night (past EVENING), a citizen in SLEEP stays there and one that
     * should go to bed does; by day, one in SLEEP or asleep wakes up.
     */
    public static Verdict decide(
            GameClock clock, boolean inSleepState, boolean asleep, Optional<BlockPos> home, BlockPos at) {
        if (clock.dayTime() > EVENING) {
            if (inSleepState) {
                return Verdict.STAY_ASLEEP;
            }
            return shouldGoSleep(clock, home, at) ? Verdict.GO_TO_SLEEP : Verdict.NONE;
        }
        return inSleepState || asleep ? Verdict.WAKE_UP : Verdict.NONE;
    }

    /**
     * MC shouldGoSleep: leaves just in time to be home at NIGHT, at TIME_PER_BLOCK per block of a distance weighting
     * height by Y_DIFF_WEIGHT, truncated to int as MC; false without a home position.
     *
     * <p>Deviation from MC: MC counts the walk in day ticks, which are real ticks in Minecraft; with the day mapped by
     * phases (SP4 spec § 4) both the time left and the walk are real ticks.
     */
    public static boolean shouldGoSleep(GameClock clock, Optional<BlockPos> home, BlockPos at) {
        if (home.isEmpty()) {
            return false;
        }
        BlockPos h = home.get();
        int xDiff = Math.abs(h.x() - at.x());
        int zDiff = Math.abs(h.z() - at.z());
        int yDiff = (int) (Math.abs(h.y() - at.y()) * Y_DIFF_WEIGHT);
        double timeNeeded =
                Math.sqrt((double) xDiff * xDiff + (double) zDiff * zDiff + (double) yDiff * yDiff) * TIME_PER_BLOCK;
        long timeLeft = clock.dayTime() >= NIGHT ? 0 : clock.realTicksUntil(NIGHT);
        return timeLeft <= 0 || timeLeft - timeNeeded <= 0;
    }
}
