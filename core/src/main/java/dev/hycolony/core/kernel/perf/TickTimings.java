package dev.hycolony.core.kernel.perf;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.LongSupplier;

/**
 * How long each part of the core takes, over the last minute of core ticks, kept in one-second buckets: what HyLens
 * shows of HyColony, which Hytale measures as one system. Recording allocates nothing once a part was seen. World
 * thread.
 *
 * <p>Deviation from MC: MineColonies measures nothing of its own (debugging addition).
 */
public final class TickTimings {
    /** Core ticks in a second (CLAUDE.md § 6, 20 ticks/s). */
    static final int TICKS_PER_SECOND = 20;
    /** Seconds kept. */
    static final int SECONDS = 60;

    private final LongSupplier nanos;
    private final LongSupplier tick;
    private final Map<String, Part> parts = new LinkedHashMap<>();

    /** Timings read from {@code nanos} (System::nanoTime), bucketed by the core's {@code tick}. */
    public TickTimings(LongSupplier nanos, LongSupplier tick) {
        this.nanos = nanos;
        this.tick = tick;
    }

    /** The time a part starts, to hand to {@link #stop}. */
    public long start() {
        return nanos.getAsLong();
    }

    /** Records that {@code part}, started at {@code start}, ends now. */
    public void stop(String part, long start) {
        long took = nanos.getAsLong() - start;
        parts.computeIfAbsent(part, Part::new).add(tick.getAsLong() / TICKS_PER_SECOND, took);
    }

    /**
     * {@code task}, timed as {@code part} each time it runs: wrap it once, where it is registered, so that running it
     * allocates nothing.
     */
    public Runnable timed(String part, Runnable task) {
        return () -> {
            long start = start();
            try {
                task.run();
            } finally {
                stop(part, start);
            }
        };
    }

    /** Each part run within the last minute, the heaviest (most time in all) first. */
    public List<PartTime> lastMinute() {
        long now = tick.getAsLong() / TICKS_PER_SECOND;
        List<PartTime> out = new ArrayList<>(parts.size());
        for (Part p : parts.values()) {
            p.over(now).ifPresent(out::add);
        }
        out.sort(Comparator.comparingLong(PartTime::totalNanos).reversed());
        return out;
    }

    /** One part's buckets: the second each holds, and its calls, total and worst. */
    private static final class Part {
        private final String name;
        private final long[] second = new long[SECONDS];
        private final long[] calls = new long[SECONDS];
        private final long[] total = new long[SECONDS];
        private final long[] max = new long[SECONDS];

        Part(String name) {
            this.name = name;
            Arrays.fill(second, -1);
        }

        void add(long now, long took) {
            int i = (int) Math.floorMod(now, (long) SECONDS);
            if (second[i] != now) {
                second[i] = now;
                calls[i] = 0;
                total[i] = 0;
                max[i] = 0;
            }
            calls[i]++;
            total[i] += took;
            max[i] = Math.max(max[i], took);
        }

        /** Its time over the minute ending at second {@code now}; empty if it did not run then. */
        Optional<PartTime> over(long now) {
            long n = 0;
            long sum = 0;
            long worst = 0;
            for (int i = 0; i < SECONDS; i++) {
                if (second[i] > now - SECONDS && second[i] <= now) {
                    n += calls[i];
                    sum += total[i];
                    worst = Math.max(worst, max[i]);
                }
            }
            return n == 0 ? Optional.empty() : Optional.of(new PartTime(name, n, sum, worst));
        }
    }
}
