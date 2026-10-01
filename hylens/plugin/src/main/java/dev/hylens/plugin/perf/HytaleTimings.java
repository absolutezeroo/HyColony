package dev.hylens.plugin.perf;

import com.hypixel.hytale.component.ComponentRegistry;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.metrics.metric.HistoricMetric;
import com.hypixel.hytale.server.core.universe.world.World;
import dev.hycolony.api.debug.PartTiming;
import dev.hylens.core.perf.PerfSnapshot;
import dev.hylens.core.perf.Period;
import dev.hylens.core.perf.SystemMetrics;
import dev.hylens.core.perf.SystemTime;
import dev.hylens.core.perf.WorldTick;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.jspecify.annotations.Nullable;

/**
 * What Hytale measures of a world, as its own /server dump reads it (DumpUtil.printComponentStore): the length of the
 * world's ticks (TickingThread) and of each ticking system's (Store.tick times each one, always), per period. World
 * thread.
 */
final class HytaleTimings {
    /** The period the chat report reads: one minute. */
    private static final long MINUTE_NANOS = 60_000_000_000L;

    private HytaleTimings() {}

    /** The world's tick over the last minute, and the ticks per second it aims at. */
    static WorldTick tick(World world) {
        Period minute = minute(periods(world.getBufferedTickLengthMetricSet()));
        return new WorldTick(minute.avgNanos(), minute.maxNanos(), world.getTps());
    }

    /** Every ticking system of the world's entity and chunk stores, over the last minute. */
    static List<SystemTime> systems(World world) {
        List<SystemTime> out = new ArrayList<>();
        for (SystemMetrics s : all(world)) {
            Period minute = minute(s.periods());
            out.add(new SystemTime(s.className(), minute.avgNanos(), minute.maxNanos()));
        }
        return out;
    }

    /** Everything of the world for the dump, at {@code time}, with HyColony's {@code colony} parts. */
    static PerfSnapshot snapshot(World world, String time, Optional<List<PartTiming>> colony) {
        return new PerfSnapshot(
                world.getName(),
                time,
                world.getTps(),
                periods(world.getBufferedTickLengthMetricSet()),
                all(world),
                colony);
    }

    private static List<SystemMetrics> all(World world) {
        List<SystemMetrics> out = new ArrayList<>();
        add(out, "EntityStore", world.getEntityStore().getStore());
        add(out, "ChunkStore", world.getChunkStore().getStore());
        return out;
    }

    private static <T> void add(List<SystemMetrics> out, String name, Store<T> store) {
        ComponentRegistry.Data<T> data = store.getRegistry().getData();
        @Nullable HistoricMetric[] metrics = store.getSystemMetrics();
        for (int i = 0; i < Math.min(metrics.length, data.getSystemSize()); i++) {
            @Nullable HistoricMetric m = metrics[i];
            if (m != null) {
                out.add(new SystemMetrics(
                        data.getSystem(i).getClass().getName(), name, store.getEntityCountFor(i), periods(m)));
            }
        }
    }

    /** Each of {@code m}'s periods with its average and worst value (0 for a period still empty). */
    private static List<Period> periods(HistoricMetric m) {
        long[] lengths = m.getPeriodsNanos();
        List<Period> out = new ArrayList<>(lengths.length);
        for (int p = 0; p < lengths.length; p++) {
            out.add(new Period(lengths[p], m.getAverage(p), Math.max(0, m.calculateMax(p))));
        }
        return out;
    }

    /** The one-minute period, else the longest. */
    private static Period minute(List<Period> periods) {
        return periods.stream()
                .filter(p -> p.nanos() == MINUTE_NANOS)
                .findFirst()
                .orElse(periods.getLast());
    }
}
