package dev.hycolony.core.colony;

import dev.hycolony.core.building.Building;

/** Colony-level events posted on the world EventBus. */
public final class ColonyEvents {
    private ColonyEvents() {}

    public record ColonyCreated(Colony colony) {}
    public record ColonyDeleted(int colonyId) {}
    public record BuildingPlaced(Colony colony, Building building) {}
    public record BuildingRemoved(Colony colony, Building building) {}
    public record DayStarted(Colony colony) {}
    public record NightFell(Colony colony) {}
}
