package dev.hycolony.core;

import dev.hycolony.core.building.BuildingRegistry;
import dev.hycolony.core.building.BuildingType;
import dev.hycolony.core.building.BuildingTypes;
import dev.hycolony.core.construction.hut.ConstructionBuildingTypes;
import dev.hycolony.core.job.JobRegistry;
import dev.hycolony.core.logistics.courier.DeliverymanHut;
import dev.hycolony.core.logistics.warehouse.WarehouseBuilding;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Predicate;

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

    /**
     * Registers a sub-plugin's types all together or not at all: the pack first runs on scratch registries, then none
     * of its ids or hut keys may already be registered and each hut key must pass {@code hutKeyKnown} (the merged id-map has it).
     * Returns the problems found, empty when the types are registered. A pack that throws registers nothing either:
     * its exception reaches the caller.
     */
    public static List<String> registerPack(
            FeaturePack pack, BuildingRegistry buildings, JobRegistry jobs, Predicate<String> hutKeyKnown) {
        BuildingRegistry newBuildings = new BuildingRegistry();
        JobRegistry newJobs = new JobRegistry();
        pack.register(newBuildings, newJobs);
        List<String> problems = new ArrayList<>();
        for (BuildingType type : newBuildings.all()) {
            if (buildings.byId(type.id()).isPresent()) {
                problems.add("building type " + type.id() + " is already registered");
            }
            buildings
                    .byHutKey(type.hutBlockKey())
                    .ifPresent(other -> problems.add("hut key " + type.hutBlockKey() + " of " + type.id()
                            + " is already used by " + other.id()));
            if (!hutKeyKnown.test(type.hutBlockKey())) {
                problems.add("hut key " + type.hutBlockKey() + " of " + type.id() + " is not in the id-map");
            }
        }
        newJobs.all().stream()
                .filter(type -> jobs.byId(type.id()).isPresent())
                .forEach(type -> problems.add("job type " + type.id() + " is already registered"));
        if (problems.isEmpty()) {
            newBuildings.all().forEach(buildings::register);
            newJobs.all().forEach(jobs::register);
        }
        return List.copyOf(problems);
    }
}
