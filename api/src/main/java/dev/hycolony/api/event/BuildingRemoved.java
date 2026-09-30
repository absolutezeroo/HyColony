package dev.hycolony.api.event;

import dev.hycolony.api.Actor;
import dev.hycolony.api.ColonyRef;
import dev.hycolony.api.Pos;

/**
 * A building left the colony: its hut was broken or picked up by a player, or found gone ({@code cause} is
 * then the colony).
 *
 * @since 1.0
 */
public record BuildingRemoved(ColonyRef colony, String type, Pos position, Actor cause) {}
