package dev.hylens.core.perf;

/** One of Hytale's ticking systems over the last minute: its class name, its average and worst tick, in ns. */
public record SystemTime(String className, double avgNanos, long maxNanos) {}
