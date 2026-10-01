package dev.hylens.core.perf;

import dev.hycolony.api.ApiText;
import dev.hycolony.api.debug.PartTiming;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

/**
 * What /hylens perf tells of a world over the last minute: its tick against its budget, Hytale's heaviest ticking
 * systems (HyColony's core is one of them), then HyColony's own parts, as its core times them.
 */
public final class PerfReport {
    /** How many systems, and parts, are told: the heaviest. */
    static final int TOP = 8;

    private static final double NANOS_PER_MS = 1_000_000.0;
    /** Seconds the parts were timed over. */
    private static final int MINUTE_SECONDS = 60;

    private PerfReport() {}

    /**
     * The lines for the world's {@code tick}, its {@code systems}, and HyColony's {@code colony} parts (empty where
     * HyColony does not run), heaviest first.
     */
    public static List<ApiText> lines(WorldTick tick, List<SystemTime> systems, Optional<List<PartTiming>> colony) {
        List<ApiText> out = new ArrayList<>();
        out.add(ApiText.of(
                "hylens.perf.world",
                ms(tick.avgNanos(), "%.2f"),
                ms((double) tick.maxNanos(), "%.1f"),
                ms(1e9 / tick.tps(), "%.1f")));
        out.add(ApiText.of("hylens.perf.systems"));
        systems.stream()
                .sorted(Comparator.comparingDouble(SystemTime::avgNanos).reversed())
                .limit(TOP)
                .forEach(s -> out.add(ApiText.of(
                        "hylens.perf.system",
                        simple(s.className()),
                        ms(s.avgNanos(), "%.3f"),
                        ms((double) s.maxNanos(), "%.1f"))));
        out.addAll(colony.map(PerfReport::parts).orElse(List.of(ApiText.of("hylens.notRunning"))));
        return out;
    }

    /** HyColony's heaviest parts, each in ms per second, its calls and its worst call; a line if none ran. */
    private static List<ApiText> parts(List<PartTiming> parts) {
        if (parts.isEmpty()) {
            return List.of(ApiText.of("hylens.perf.colonyIdle"));
        }
        List<ApiText> out = new ArrayList<>();
        out.add(ApiText.of("hylens.perf.colony"));
        parts.stream()
                .limit(TOP)
                .forEach(p -> out.add(ApiText.of(
                        "hylens.perf.part",
                        p.part(),
                        ms((double) p.totalNanos() / MINUTE_SECONDS, "%.3f"),
                        String.valueOf(p.calls()),
                        ms((double) p.maxNanos(), "%.1f"))));
        return out;
    }

    /** The class name without its package. */
    private static String simple(String className) {
        return className.substring(className.lastIndexOf('.') + 1);
    }

    private static String ms(double nanos, String format) {
        return String.format(Locale.ROOT, format, nanos / NANOS_PER_MS);
    }
}
