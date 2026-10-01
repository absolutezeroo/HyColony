package dev.hycolony.core.testing;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

/** The fake clock's MC day time, which the sleep tests rely on. */
class FakeClockTest {
    @Test
    void realTicksUntilWrapsAroundTheDayAndStopsWhenPaused() {
        FakeClock c = new FakeClock();
        c.dayTime = 12000;
        c.realTicksPerDayTick = 2;

        assertEquals(1200, c.realTicksUntil(12600));
        assertEquals(2 * 23400, c.realTicksUntil(11400));
        c.paused = true;
        assertEquals(Long.MAX_VALUE, c.realTicksUntil(12600));
    }
}
