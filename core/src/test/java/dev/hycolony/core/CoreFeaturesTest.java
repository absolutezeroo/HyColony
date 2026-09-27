package dev.hycolony.core;

import static org.junit.jupiter.api.Assertions.assertEquals;

import dev.hycolony.core.building.BuildingRegistry;
import dev.hycolony.core.building.BuildingTypes;
import dev.hycolony.core.construction.builder.BuilderJob;
import dev.hycolony.core.construction.hut.ConstructionBuildingTypes;
import dev.hycolony.core.job.JobRegistry;
import dev.hycolony.core.logistics.courier.DeliverymanHut;
import dev.hycolony.core.logistics.courier.DeliverymanJob;
import dev.hycolony.core.logistics.warehouse.WarehouseBuilding;
import java.util.List;
import org.junit.jupiter.api.Test;

class CoreFeaturesTest {

    @Test
    void coreFeaturesRegisterEveryCoreBuildingAndJobTypeOnce() {
        BuildingRegistry buildings = new BuildingRegistry();
        JobRegistry jobs = new JobRegistry();

        CoreFeatures.register(buildings, jobs);

        assertEquals(
                List.of(
                        BuildingTypes.TOWN_HALL,
                        ConstructionBuildingTypes.BUILDER,
                        ConstructionBuildingTypes.RESIDENCE,
                        WarehouseBuilding.TYPE,
                        DeliverymanHut.TYPE),
                buildings.all());
        assertEquals(List.of(BuilderJob.TYPE, DeliverymanJob.TYPE), jobs.all());
    }
}
