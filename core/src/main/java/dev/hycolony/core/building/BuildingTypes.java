package dev.hycolony.core.building;

import java.util.List;

/** The building types owned by the colony itself (the town hall). MC ModBuildings. */
public final class BuildingTypes {
    public static final BuildingType TOWN_HALL = new BuildingType("hycolony:townhall", "hut.townhall", 5, List.of());

    private BuildingTypes() {}

    /** Registers the town hall; the other features register their own huts through {@code CoreFeatures}. */
    public static void register(BuildingRegistry registry) {
        registry.register(TOWN_HALL);
    }
}
