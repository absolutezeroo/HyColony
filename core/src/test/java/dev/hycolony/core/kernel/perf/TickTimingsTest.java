package dev.hycolony.core.kernel.perf;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.List;
import org.junit.jupiter.api.Test;

/** How long each part of the core takes, over the last minute of core ticks (HyLens's /hylens perf). */
class TickTimingsTest {
    private long nanos;
    private long tick;
    private final TickTimings timings = new TickTimings(() -> nanos, () -> tick);

    private void run(String part, long took) {
        long start = timings.start();
        nanos += took;
        timings.stop(part, start);
    }

    @Test
    void partsAddUpTheirCallsAndKeepTheirWorst() {
        run("requests", 300);
        run("requests", 100);
        run("hycolony:farmer", 50);

        assertEquals(
                List.of(new PartTime("requests", 2, 400, 300), new PartTime("hycolony:farmer", 1, 50, 50)),
                timings.lastMinute());
    }

    @Test
    void timedTaskRunsAndIsTimedThoughItThrows() {
        int[] runs = {0};
        Runnable task = timings.timed("autosave", () -> {
            runs[0]++;
            nanos += 70;
            throw new IllegalStateException("disk full");
        });

        assertThrows(IllegalStateException.class, task::run);

        assertEquals(1, runs[0]);
        assertEquals(List.of(new PartTime("autosave", 1, 70, 70)), timings.lastMinute());
    }

    @Test
    void heaviestPartComesFirst() {
        run("light", 10);
        run("heavy", 1_000);

        assertEquals("heavy", timings.lastMinute().getFirst().part());
    }

    @Test
    void aMinuteLaterAPartIsForgotten() {
        run("autosave", 5_000);
        tick += 20 * 60;
        run("requests", 10);

        assertEquals(List.of(new PartTime("requests", 1, 10, 10)), timings.lastMinute());
    }

    @Test
    void secondsWithinTheMinuteAllCount() {
        run("requests", 10);
        tick += 20 * 59;
        run("requests", 20);

        assertEquals(List.of(new PartTime("requests", 2, 30, 20)), timings.lastMinute());
    }

    @Test
    void aBucketReusedAMinuteLaterStartsAfresh() {
        run("requests", 1_000);
        tick += 20 * 60;
        run("requests", 1);
        tick += 20;

        assertEquals(List.of(new PartTime("requests", 1, 1, 1)), timings.lastMinute());
    }
}
