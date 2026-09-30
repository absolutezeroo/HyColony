package dev.hycolony.api.event;

import dev.hycolony.api.ColonyRef;

/**
 * The colony's day {@code day} started.
 *
 * @since 1.0
 */
public record DayStarted(ColonyRef colony, int day) {}
