package dev.hycolony.core.kernel.perf;

/** How long a part of the core took over the last minute: its {@code calls}, their total and the worst, in ns. */
public record PartTime(String part, long calls, long totalNanos, long maxNanos) {}
