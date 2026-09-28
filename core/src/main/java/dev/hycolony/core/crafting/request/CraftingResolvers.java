package dev.hycolony.core.crafting.request;

import dev.hycolony.core.building.Building;
import dev.hycolony.core.colony.Colony;
import dev.hycolony.core.request.Resolver;
import java.util.List;

/**
 * The resolvers a hut's crafting module offers the colony (MC AbstractCraftingBuildingModule.createResolvers, and the
 * private ones of WorkerBuildingModule.createResolvers). At equal priority and suitability the first registered wins.
 * Deviation from MC: the public ones always come first; MC registers them in the order of the hut's modules, and the
 * farmer lists its crafting module before its workers, as here. Resolver ids hold the job id: a hut has at most one
 * crafting module per job.
 */
public final class CraftingResolvers {
    private CraftingResolvers() {}

    /** The public, then the private crafting request resolvers of {@code hut}, for {@code jobId}'s crafters. */
    public static List<Resolver> of(Colony colony, Building hut, String jobId) {
        return List.of(
                new CraftingRequestResolver(colony, hut, jobId, true),
                new CraftingRequestResolver(colony, hut, jobId, false));
    }
}
