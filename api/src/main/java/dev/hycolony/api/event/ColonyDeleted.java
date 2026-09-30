package dev.hycolony.api.event;

import dev.hycolony.api.Actor;
import dev.hycolony.api.ColonyRef;

/**
 * A colony was deleted; {@code cause} is the player who deleted it.
 *
 * @since 1.0
 */
public record ColonyDeleted(ColonyRef colony, Actor cause) {}
