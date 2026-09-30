package dev.hycolony.api.debug;

import dev.hycolony.api.ApiText;
import dev.hycolony.api.Experimental;

/**
 * One thing a tracked citizen did at {@code tick}, of {@code kind} ({@code AI_STATE}, {@code JOB_STEP},
 * {@code WALK_ENDED}, {@code STUCK}): its AI or job went from {@code from} to {@code to} ({@code ""} for none), a walk
 * to {@code from} ended {@code to}, or the stuck handler took the action {@code to} on it; {@code detail} says it.
 *
 * @since 1.0
 */
@Experimental
public record HistoryEntry(long tick, String kind, String from, String to, ApiText detail) {}
