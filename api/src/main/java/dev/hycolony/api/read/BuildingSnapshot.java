package dev.hycolony.api.read;

import dev.hycolony.api.ColonyRef;
import dev.hycolony.api.Pos;

/**
 * A building as it was when read: its type's id ({@code hycolony:townhall}...), its hut block's position, its level
 * (0 before it is built), whether it is built, and its style ({@code ""} before one is chosen).
 *
 * @since 1.0
 */
public record BuildingSnapshot(ColonyRef colony, String type, Pos position, int level, boolean built, String style) {}
