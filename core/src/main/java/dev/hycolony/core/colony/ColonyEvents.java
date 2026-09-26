package dev.hycolony.core.colony;

import dev.hycolony.core.building.Building;
import dev.hycolony.core.construction.workorder.WorkOrder;

/** Colony-level events posted on the world EventBus. */
public final class ColonyEvents {
    private ColonyEvents() {}

    public record ColonyCreated(Colony colony) {}

    public record ColonyDeleted(int colonyId) {}

    public record BuildingPlaced(Colony colony, Building building) {}

    public record BuildingRemoved(Colony colony, Building building) {}

    public record DayStarted(Colony colony) {}

    public record NightFell(Colony colony) {}

    public record WorkOrderCreated(Colony colony, WorkOrder order) {}
    /** A builder completed an order on {@code building}; old == new for REPAIR and REMOVE. */
    public record BuildingLevelChanged(Colony colony, Building building, int oldLevel, int newLevel) {}
}
