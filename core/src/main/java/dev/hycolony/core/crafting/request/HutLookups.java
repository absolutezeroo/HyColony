package dev.hycolony.core.crafting.request;

import dev.hycolony.core.building.Building;
import dev.hycolony.core.building.BuildingModule;
import dev.hycolony.core.crafting.module.CraftingModule;
import dev.hycolony.core.job.WorkerModule;
import dev.hycolony.core.request.Resolver;
import dev.hycolony.core.request.model.RequesterId;
import java.util.ArrayList;
import java.util.List;

/** What the crafting resolvers look up in their hut (MC AbstractBuilding and the requester's location). */
final class HutLookups {
    private HutLookups() {}

    /** MC getModulesByType(ICraftingBuildingModule.class): the hut's crafting modules, in module order. */
    static List<CraftingModule> craftingModules(Building hut) {
        List<CraftingModule> out = new ArrayList<>();
        for (BuildingModule module : hut.modules().values()) {
            if (module instanceof CraftingModule crafting) {
                out.add(crafting);
            }
        }
        return out;
    }

    /** MC AbstractCraftingRequestResolver.hasModuleForJob: a worker module of the hut for this job has a worker. */
    static boolean employs(Building hut, String jobId) {
        for (BuildingModule module : hut.modules().values()) {
            if (module instanceof WorkerModule workers
                    && workers.job().id().equals(jobId)
                    && !workers.workers().isEmpty()) {
                return true;
            }
        }
        return false;
    }

    /**
     * MC {@code requester.getLocation().equals(getLocation())}: the hut itself asks, or one of its resolvers asks for
     * a child; every other requester stands elsewhere.
     */
    static boolean isAt(Building hut, RequesterId requester) {
        if (hut.requesterId().equals(requester)) {
            return true;
        }
        for (Resolver resolver : hut.resolvers()) {
            if (resolver.requesterId().equals(requester)) {
                return true;
            }
        }
        return false;
    }
}
