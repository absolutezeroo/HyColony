package dev.hycolony.core.building;

import java.util.List;

/** The building types owned by the colony itself (the town hall). MC ModBuildings. */
public final class BuildingTypes {
    public static final BuildingType TOWN_HALL = new BuildingType("hycolony:townhall", "hut.townhall", 5, List.of());

    private BuildingTypes() {}

    /** A registry with the town hall; each feature (construction…) registers its own huts on top. */
    public static BuildingRegistry defaults() {
        BuildingRegistry registry = new BuildingRegistry();
        registry.register(TOWN_HALL);
        return registry;
    }
}
