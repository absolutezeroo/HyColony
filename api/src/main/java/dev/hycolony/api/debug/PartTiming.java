package dev.hycolony.api.debug;

import dev.hycolony.api.Experimental;

/**
 * How long a part of HyColony's core took over the last minute of its ticks: how many times it ran ({@code calls}),
 * their total and the worst one, in nanoseconds. A part is named as the core names it: {@code "requests"},
 * {@code "work orders"}, {@code "autosave"}..., a citizen's AI by its job's id ({@code "citizen"} without a job).
 *
 * @since 1.0
 */
@Experimental
public record PartTiming(String part, long calls, long totalNanos, long maxNanos) {}
