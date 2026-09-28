package dev.hycolony.core.crafting.job;

import dev.hycolony.core.building.Building;
import dev.hycolony.core.building.BuildingModule;
import dev.hycolony.core.citizen.CitizenData;
import dev.hycolony.core.colony.Colony;
import dev.hycolony.core.job.WorkerModule;
import dev.hycolony.core.request.model.RequestToken;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.jspecify.annotations.Nullable;

/** Where the crafters are: the citizens whose job is a {@link Crafter} (MC {@code instanceof AbstractJobCrafter}). */
public final class Crafters {
    private Crafters() {}

    /**
     * MC getAllAssignedCitizen filtered on crafter jobs: the hut's workers, of every job, that craft, in module then
     * hiring order.
     */
    public static List<Crafter> ofHut(Colony colony, Building hut) {
        return collect(colony, hut, null);
    }

    /**
     * MC {@code getModuleMatching(CraftingWorkerBuildingModule.class, jobEntry).getAssignedCitizen()} filtered on crafter
     * jobs: the hut's workers for {@code jobId} that craft, in hiring order.
     */
    public static List<Crafter> ofJob(Colony colony, Building hut, String jobId) {
        return collect(colony, hut, jobId);
    }

    /** MC removeRequestFromTaskList's search: the first crafter of the colony holding the task, queued or scheduled. */
    public static Optional<Crafter> holding(Colony colony, RequestToken token) {
        for (CitizenData citizen : colony.citizens().all()) {
            if (citizen.job().orElse(null) instanceof Crafter crafter && holds(crafter.craftingTasks(), token)) {
                return Optional.of(crafter);
            }
        }
        return Optional.empty();
    }

    private static boolean holds(CraftingTasks tasks, RequestToken token) {
        return tasks.taskQueue().contains(token) || tasks.assignedTasks().contains(token);
    }

    /** The crafters of the hut's worker modules for {@code jobId}, or of all of them if null. */
    private static List<Crafter> collect(Colony colony, Building hut, @Nullable String jobId) {
        List<Crafter> out = new ArrayList<>();
        for (BuildingModule module : hut.modules().values()) {
            if (module instanceof WorkerModule workers
                    && (jobId == null || workers.job().id().equals(jobId))) {
                for (int id : workers.workers()) {
                    colony.citizens()
                            .get(id)
                            .flatMap(CitizenData::job)
                            .filter(Crafter.class::isInstance)
                            .map(Crafter.class::cast)
                            .ifPresent(out::add);
                }
            }
        }
        return out;
    }
}
