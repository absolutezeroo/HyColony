package dev.hycolony.core.logistics.warehouse;

import dev.hycolony.core.building.BuildingRegistry;
import dev.hycolony.core.building.BuildingType;
import dev.hycolony.core.building.module.ModuleProducer;
import java.util.List;

/**
 * The warehouse hut type (MC {@code BuildingWareHouse}, modules from {@code ModBuildingsInitializer}): nobody works
 * there, couriers are attached to it. Its racks are {@code Building.containers()}. Storage upgrades and minimum stock
 * are not ported yet (SP3a backlog).
 */
public final class WarehouseBuilding {
    public static final String TYPE_ID = "hycolony:warehouse";
    /** MC BuildingWareHouse.MAX_LEVEL. */
    public static final int MAX_LEVEL = 5;

    public static final BuildingType TYPE = new BuildingType(
            TYPE_ID,
            "hut.warehouse",
            MAX_LEVEL,
            List.of(
                    new ModuleProducer("couriers", CourierAssignmentModule::new),
                    new ModuleProducer("requestQueue", WarehouseRequestQueue::new),
                    new ModuleProducer("storage", WarehouseStorage::new),
                    new ModuleProducer("resolvers", WarehouseResolvers::new)));

    private WarehouseBuilding() {}

    /** Registers the warehouse hut type. */
    public static void register(BuildingRegistry r) {
        r.register(TYPE);
    }
}
