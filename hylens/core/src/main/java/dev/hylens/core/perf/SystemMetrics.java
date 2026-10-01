package dev.hylens.core.perf;

import java.util.List;

/** One of Hytale's ticking systems: its class, its store, the entities it runs on, and its tick per period. */
public record SystemMetrics(String className, String store, int entities, List<Period> periods) {
    /** Keeps its own copy of the periods. */
    public SystemMetrics {
        periods = List.copyOf(periods);
    }
}
