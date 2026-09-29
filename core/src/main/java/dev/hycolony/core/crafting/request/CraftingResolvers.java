package dev.hycolony.core.crafting.request;

import dev.hycolony.core.building.Building;
import dev.hycolony.core.building.module.CreatesResolvers;
import dev.hycolony.core.colony.Colony;
import dev.hycolony.core.crafting.module.CraftingModule;
import dev.hycolony.core.crafting.module.CraftingModules;
import dev.hycolony.core.request.Resolver;
import java.util.ArrayList;
import java.util.List;

/**
 * The resolvers a hut's crafting modules offer the colony (MC AbstractCraftingBuildingModule.createResolvers, and the
 * private ones of WorkerBuildingModule.createResolvers). A crafter hut type declares this module next to its crafting
 * modules, as the warehouse declares its {@code WarehouseResolvers}. At equal priority and suitability the first
 * registered wins. Resolver ids hold the job id: a hut has at most one crafting module per job.
 *
 * <p>Deviation from MC: the resolvers come from this module, where MC's crafting module creates its own, so that
 * {@code crafting.module} does not depend on {@code crafting.request}; the private ones come from here too, where MC's
 * WorkerBuildingModule makes them, so that {@code job} does not depend on {@code crafting}. For each crafting module
 * the public ones come first; MC registers them in the order of the hut's modules, and the farmer lists its crafting
 * module before its workers, as here.
 */
public final class CraftingResolvers implements CreatesResolvers {
    /**
     * For each crafting module of {@code hut}, in module order: its public crafting request and production resolvers,
     * then the private ones, for its job's crafters (MC's order within each module).
     */
    @Override
    public List<Resolver> createResolvers(Colony colony, Building hut) {
        List<Resolver> out = new ArrayList<>();
        for (CraftingModule module : CraftingModules.of(hut)) {
            String jobId = module.jobId();
            out.add(new CraftingRequestResolver(colony, hut, jobId, true));
            out.add(new CraftingProductionResolver(colony, hut, jobId, true));
            out.add(new CraftingRequestResolver(colony, hut, jobId, false));
            out.add(new CraftingProductionResolver(colony, hut, jobId, false));
        }
        return out;
    }
}
