package dev.hycolony.core.building;

import dev.hycolony.core.construction.ConstructionBuildingTypes;
import java.util.List;

public final class BuildingTypes {
    public static final BuildingType TOWN_HALL = new BuildingType("hycolony:townhall", "hut.townhall", 5, List.of());

    private BuildingTypes() {}

    public static BuildingRegistry defaults() {
        BuildingRegistry registry = new BuildingRegistry();
        registry.register(TOWN_HALL);
        ConstructionBuildingTypes.register(registry);
        return registry;
    }
}
