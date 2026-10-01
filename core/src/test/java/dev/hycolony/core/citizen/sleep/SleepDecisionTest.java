package dev.hycolony.core.citizen.sleep;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.hycolony.core.citizen.sleep.SleepDecision.Verdict;
import dev.hycolony.core.kernel.BlockPos;
import dev.hycolony.core.testing.FakeClock;
import java.util.Optional;
import org.junit.jupiter.api.Test;

/** MC CitizenAI.calculateNextState (sleep part) and CitizenSleepHandler.shouldGoSleep. */
class SleepDecisionTest {
    private static final BlockPos AT = new BlockPos(0, 64, 0);
    private final FakeClock clock = new FakeClock();

    private static Optional<BlockPos> homeAt(int x, int dy) {
        return Optional.of(new BlockPos(x, 64 + dy, 0));
    }

    @Test
    void noBedtimeBeforeTheEvening() {
        clock.dayTime = SleepDecision.NIGHT - 2000; // MC isPastTime(NIGHT - 2000): still the day
        assertEquals(Verdict.NONE, SleepDecision.decide(clock, false, false, homeAt(10_000, 0), AT));
    }

    @Test
    void leavesJustInTimeForItsDistance() {
        clock.dayTime = 12000; // 600 real ticks before nightfall at one real tick per MC tick
        assertTrue(SleepDecision.shouldGoSleep(clock, homeAt(100, 0), AT)); // 100 blocks: 600 ticks
        assertTrue(SleepDecision.shouldGoSleep(clock, homeAt(101, 0), AT));
        assertFalse(SleepDecision.shouldGoSleep(clock, homeAt(99, 0), AT));
        assertEquals(Verdict.GO_TO_SLEEP, SleepDecision.decide(clock, false, false, homeAt(100, 0), AT));
    }

    @Test
    void walkTimeCountsInRealTicks() {
        clock.dayTime = 12300;
        clock.realTicksPerDayTick = 2; // 300 MC ticks left are 600 real ticks
        assertTrue(SleepDecision.shouldGoSleep(clock, homeAt(100, 0), AT));
        assertFalse(SleepDecision.shouldGoSleep(clock, homeAt(99, 0), AT));
    }

    @Test
    void heightWeighsOneAndAHalfTruncated() {
        clock.dayTime = 12600 - 24; // 3 blocks up weigh (int) 4.5 = 4 blocks, so 24 ticks of walk
        assertTrue(SleepDecision.shouldGoSleep(clock, homeAt(0, 3), AT));
        clock.dayTime = 12600 - 25;
        assertFalse(SleepDecision.shouldGoSleep(clock, homeAt(0, 3), AT));
    }

    @Test
    void pastNightfallLeavesAtOnce() {
        clock.dayTime = 13000;
        assertTrue(SleepDecision.shouldGoSleep(clock, homeAt(500, 0), AT));
    }

    @Test
    void pausedClockNeverSendsHomeEarly() {
        clock.dayTime = 11000;
        clock.paused = true;
        assertEquals(Verdict.NONE, SleepDecision.decide(clock, false, false, homeAt(50, 0), AT));
    }

    @Test
    void withoutHomeNorTownHallNeverSleeps() {
        clock.dayTime = 20000;
        assertEquals(Verdict.NONE, SleepDecision.decide(clock, false, false, Optional.empty(), AT));
    }

    @Test
    void asleepStaysAsleepAtNight() {
        clock.dayTime = 20000;
        assertEquals(Verdict.STAY_ASLEEP, SleepDecision.decide(clock, true, true, Optional.empty(), AT));
    }

    @Test
    void dawnWakesTheSleepStateAndTheAsleep() {
        clock.dayTime = 0;
        assertEquals(Verdict.WAKE_UP, SleepDecision.decide(clock, true, false, homeAt(5, 0), AT));
        assertEquals(Verdict.WAKE_UP, SleepDecision.decide(clock, false, true, homeAt(5, 0), AT));
        assertEquals(Verdict.NONE, SleepDecision.decide(clock, false, false, homeAt(5, 0), AT));
    }
}
