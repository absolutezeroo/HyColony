package dev.hycolony.api.event;

import dev.hycolony.api.Actor;
import dev.hycolony.api.ColonyRef;

/**
 * A colony was founded; {@code cause} is its founder.
 *
 * @since 1.0
 */
public record ColonyCreated(ColonyRef colony, Actor cause) {}
