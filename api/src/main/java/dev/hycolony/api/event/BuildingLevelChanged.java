package dev.hycolony.api.event;

import dev.hycolony.api.Actor;
import dev.hycolony.api.ColonyRef;
import dev.hycolony.api.Pos;

/**
 * A building reached {@code newLevel}. Either a builder finished an order on it ({@code cause} is the colony;
 * {@code oldLevel} equals {@code newLevel} for a repair or a removal), or a player placed it at a level, pasting it in
 * creative or founding a colony at a copied level ({@code cause} is that player).
 *
 * @since 1.0
 */
public record BuildingLevelChanged(
        ColonyRef colony, String type, Pos position, int oldLevel, int newLevel, Actor cause) {}
