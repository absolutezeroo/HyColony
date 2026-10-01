package dev.hylens.core.perf;

import dev.hycolony.api.debug.PartTiming;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.stream.Collectors;

/**
 * The text /hylens perf --dump writes, as Hytale's /server dump lists its systems: the world's tick per period, every
 * ticking system with its average and worst tick per period (heaviest over a minute first), then every part of
 * HyColony's core over the last minute. Plain English: a file for whoever studies it, not a window.
 */
public final class PerfDump {
    private static final double NANOS_PER_MS = 1_000_000.0;
    private static final long NANOS_PER_S = 1_000_000_000L;
    private static final long MINUTE_NANOS = 60 * NANOS_PER_S;
    /** Seconds HyColony's parts are timed over. */
    private static final int MINUTE_SECONDS = 60;

    private PerfDump() {}

    /** The dump's lines. */
    public static List<String> lines(PerfSnapshot s) {
        List<String> out = new ArrayList<>();
        out.add("HyLens perf dump, world " + s.world() + ", " + s.time());
        out.add("");
        out.add(String.format(Locale.ROOT, "World tick (%d ticks/s, budget %.2f ms):", s.tps(), 1000.0 / s.tps()));
        for (Period p : s.tick()) {
            out.add(String.format(
                    Locale.ROOT,
                    "  %s: avg %.3f ms, max %.2f ms",
                    label(p.nanos()),
                    ms(p.avgNanos()),
                    ms(p.maxNanos())));
        }
        out.add("");
        out.add("Hytale systems (" + s.systems().size() + "), heaviest over a minute first;"
                + " average/worst ms per tick for each period:");
        s.systems().stream()
                .sorted(Comparator.comparingDouble(PerfDump::minuteAverage).reversed())
                .forEach(m -> out.add(system(m)));
        out.add("");
        out.addAll(s.colony().map(PerfDump::parts).orElse(List.of("HyColony does not run in this world.")));
        return out;
    }

    private static String system(SystemMetrics m) {
        String periods = m.periods().stream()
                .map(p -> String.format(
                        Locale.ROOT, "%s %.3f/%.2f", label(p.nanos()), ms(p.avgNanos()), ms(p.maxNanos())))
                .collect(Collectors.joining("  "));
        return String.format(Locale.ROOT, "  %s  %d entities  %s  %s", periods, m.entities(), m.store(), m.className());
    }

    /** HyColony's parts, heaviest first: ms per second, calls, total, average and worst call. */
    private static List<String> parts(List<PartTiming> parts) {
        List<String> out = new ArrayList<>();
        out.add("HyColony parts over the last minute (" + parts.size() + "), heaviest first:");
        for (PartTiming p : parts) {
            out.add(String.format(
                    Locale.ROOT,
                    "  %.3f ms/s  %d calls  total %.2f ms  avg %.2f ms  max %.2f ms  %s",
                    ms(p.totalNanos()) / MINUTE_SECONDS,
                    p.calls(),
                    ms(p.totalNanos()),
                    ms(p.totalNanos()) / p.calls(),
                    ms(p.maxNanos()),
                    p.part()));
        }
        return out;
    }

    /** A system's average over its one-minute period, else over its longest. */
    private static double minuteAverage(SystemMetrics m) {
        return m.periods().stream()
                .filter(p -> p.nanos() == MINUTE_NANOS)
                .findFirst()
                .or(() -> m.periods().stream().reduce((a, b) -> b))
                .map(Period::avgNanos)
                .orElse(0.0);
    }

    /** {@code 10 s}, {@code 1 min}, {@code 5 min}. */
    private static String label(long nanos) {
        return nanos % MINUTE_NANOS == 0 ? nanos / MINUTE_NANOS + " min" : nanos / NANOS_PER_S + " s";
    }

    private static double ms(double nanos) {
        return nanos / NANOS_PER_MS;
    }

    private static double ms(long nanos) {
        return (double) nanos / NANOS_PER_MS;
    }
}
