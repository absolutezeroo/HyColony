package dev.hycolony.api.event;

import dev.hycolony.api.Actor;
import dev.hycolony.api.ColonyRef;
import dev.hycolony.api.Pos;

/**
 * A work order was created: {@code type} is {@code BUILD}, {@code UPGRADE}, {@code REPAIR} or {@code REMOVE},
 * for the building at {@code building}.
 *
 * @since 1.0
 */
public record WorkOrderCreated(ColonyRef colony, int orderId, String type, Pos building, Actor cause) {}
