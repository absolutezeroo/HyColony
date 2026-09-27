package dev.hycolony.core;

import dev.hycolony.core.building.BuildingRegistry;
import dev.hycolony.core.building.BuildingTypes;
import dev.hycolony.core.construction.hut.ConstructionBuildingTypes;
import dev.hycolony.core.job.JobRegistry;
import dev.hycolony.core.logistics.courier.DeliverymanHut;
import dev.hycolony.core.logistics.warehouse.WarehouseBuilding;

/**
 * The composition root of the core's building and job types (MC ModBuildings, ModJobs): each feature declares its own
 * types, this class only lists the features. A content pack registers its types on the same registries afterwards.
 */
public final class CoreFeatures {
    private CoreFeatures() {}

    /** Registers the town hall, construction (builder, residence) and logistics (warehouse, courier) types. */
    public static void register(BuildingRegistry buildings, JobRegistry jobs) {
        BuildingTypes.register(buildings);
        ConstructionBuildingTypes.register(buildings);
        ConstructionBuildingTypes.register(jobs);
        WarehouseBuilding.register(buildings);
        DeliverymanHut.register(buildings);
        DeliverymanHut.register(jobs);
    }
}
