package dev.hycolony.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

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

    private static final BuildingType FARM = new BuildingType("pack:farm", "hut.farm", 5, List.of());
    private static final JobType FARMER = new JobType("pack:farmer", c -> null);
    private static final FeaturePack FARM_PACK = (b, j) -> {
        b.register(FARM);
        j.register(FARMER);
    };

    private final BuildingRegistry buildings = new BuildingRegistry();
    private final JobRegistry jobs = new JobRegistry();

    @Test
    void aFeaturePackRegistersItsTypesAfterTheCoreOnes() {
        CoreFeatures.register(buildings, jobs);

        List<String> problems = CoreFeatures.registerPack(FARM_PACK, buildings, jobs, key -> true);

        assertEquals(List.of(), problems);
        assertEquals(FARM, buildings.all().getLast());
        assertEquals(FARMER, jobs.all().getLast());
    }

    @Test
    void aPackThatReusesACoreIdRegistersNothing() {
        CoreFeatures.register(buildings, jobs);
        FeaturePack pack = (b, j) -> {
            b.register(FARM);
            b.register(new BuildingType(BuildingTypes.TOWN_HALL.id(), "hut.other", 1, List.of()));
        };

        List<String> problems = CoreFeatures.registerPack(pack, buildings, jobs, key -> true);

        assertEquals(List.of("building type " + BuildingTypes.TOWN_HALL.id() + " is already registered"), problems);
        assertEquals(
                BuildingTypes.TOWN_HALL,
                buildings.byId(BuildingTypes.TOWN_HALL.id()).orElseThrow());
        assertTrue(buildings.byId(FARM.id()).isEmpty());
    }

    @Test
    void aPackWhoseHutIsMissingFromTheIdMapRegistersNothing() {
        List<String> problems = CoreFeatures.registerPack(FARM_PACK, buildings, jobs, key -> !key.equals("hut.farm"));

        assertEquals(List.of("hut key hut.farm of pack:farm is not in the id-map"), problems);
        assertTrue(buildings.all().isEmpty());
        assertTrue(jobs.all().isEmpty());
    }

    @Test
    void aPackThatThrowsRegistersNothing() {
        FeaturePack pack = (b, j) -> {
            b.register(FARM);
            throw new IllegalStateException("broken pack");
        };

        assertThrows(IllegalStateException.class, () -> CoreFeatures.registerPack(pack, buildings, jobs, key -> true));
        assertTrue(buildings.all().isEmpty());
    }
}
