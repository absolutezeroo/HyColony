package dev.hylens.core.perf;

import dev.hycolony.api.debug.PartTiming;
import java.util.List;
import java.util.Optional;

/**
 * Everything /hylens perf --dump writes of a world at a {@code time}: its tick per period against its {@code tps},
 * every ticking system of Hytale, and HyColony's parts (empty where HyColony does not run).
 */
public record PerfSnapshot(
        String world,
        String time,
        int tps,
        List<Period> tick,
        List<SystemMetrics> systems,
        Optional<List<PartTiming>> colony) {
    /** Keeps its own copies of the lists. */
    public PerfSnapshot {
        tick = List.copyOf(tick);
        systems = List.copyOf(systems);
        colony = colony.map(List::copyOf);
    }
}
