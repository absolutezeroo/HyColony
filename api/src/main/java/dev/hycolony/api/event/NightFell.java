package dev.hycolony.api.event;

import dev.hycolony.api.ColonyRef;

/**
 * Night fell on the colony.
 *
 * @since 1.0
 */
public record NightFell(ColonyRef colony) {}
