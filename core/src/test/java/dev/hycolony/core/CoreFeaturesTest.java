package dev.hycolony.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import dev.hycolony.core.building.BuildingRegistry;
import dev.hycolony.core.building.BuildingType;
import dev.hycolony.core.building.BuildingTypes;
import dev.hycolony.core.construction.builder.BuilderJob;
import dev.hycolony.core.construction.hut.ConstructionBuildingTypes;
import dev.hycolony.core.job.JobRegistry;
import dev.hycolony.core.job.JobType;
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

    @Test
    void aFeaturePackRegistersItsTypesAfterTheCoreOnes() {
        BuildingRegistry buildings = new BuildingRegistry();
        JobRegistry jobs = new JobRegistry();
        BuildingType farm = new BuildingType("pack:farm", "hut.farm", 5, List.of());
        JobType farmer = new JobType("pack:farmer", c -> null);
        FeaturePack pack = (b, j) -> {
            b.register(farm);
            j.register(farmer);
        };

        CoreFeatures.register(buildings, jobs);
        pack.register(buildings, jobs);

        assertEquals(farm, buildings.all().getLast());
        assertEquals(farmer, jobs.all().getLast());
    }

    @Test
    void aFeaturePackCannotReplaceACoreType() {
        BuildingRegistry buildings = new BuildingRegistry();
        JobRegistry jobs = new JobRegistry();
        CoreFeatures.register(buildings, jobs);
        FeaturePack pack =
                (b, j) -> b.register(new BuildingType(BuildingTypes.TOWN_HALL.id(), "hut.other", 1, List.of()));

        assertThrows(IllegalArgumentException.class, () -> pack.register(buildings, jobs));
        assertEquals(
                BuildingTypes.TOWN_HALL,
                buildings.byId(BuildingTypes.TOWN_HALL.id()).orElseThrow());
    }
}
