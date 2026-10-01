package dev.hylens.core.perf;

/** A world's tick over the last minute: its average and worst length, in ns, and the ticks per second it aims at. */
public record WorldTick(double avgNanos, long maxNanos, int tps) {}
