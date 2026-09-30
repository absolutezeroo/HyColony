package dev.hycolony.core.colony;

import dev.hycolony.core.building.Building;
import dev.hycolony.core.construction.workorder.WorkOrder;
import java.util.Optional;
import java.util.UUID;

/**
 * Colony-level events posted on the world EventBus. {@code player} is who caused the change; empty when the colony
 * itself did (its builder, its own upkeep).
 */
public final class ColonyEvents {
    private ColonyEvents() {}

    public record ColonyCreated(Colony colony, Optional<UUID> player) {}

    public record ColonyDeleted(int colonyId, Optional<UUID> player) {}

    public record BuildingPlaced(Colony colony, Building building, Optional<UUID> player) {}

    public record BuildingRemoved(Colony colony, Building building, Optional<UUID> player) {}

    public record DayStarted(Colony colony) {}

    public record NightFell(Colony colony) {}

    public record WorkOrderCreated(Colony colony, WorkOrder order, Optional<UUID> player) {}

    /**
     * {@code building} reached {@code newLevel}: a builder completed an order (old == new for REPAIR and REMOVE; no
     * player), or {@code player} placed it at a level (a creative paste, a founding copied at a level).
     */
    public record BuildingLevelChanged(
            Colony colony, Building building, int oldLevel, int newLevel, Optional<UUID> player) {}
}
