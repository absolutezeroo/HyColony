package dev.hylens.core.perf;

/** One of a metric's periods: its length, and the average and worst value over it, all in ns. */
public record Period(long nanos, double avgNanos, long maxNanos) {}
