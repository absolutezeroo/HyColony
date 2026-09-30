package dev.hycolony.api.event;

import dev.hycolony.api.Actor;
import dev.hycolony.api.ColonyRef;
import dev.hycolony.api.Pos;

/**
 * A hut was placed: a building of {@code type} ({@code hycolony:builder}...) joined the colony at
 * {@code position}, its hut block.
 *
 * @since 1.0
 */
public record BuildingPlaced(ColonyRef colony, String type, Pos position, Actor cause) {}
