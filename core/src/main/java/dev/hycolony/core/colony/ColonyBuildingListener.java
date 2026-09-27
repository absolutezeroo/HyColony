package dev.hycolony.core.colony;

import dev.hycolony.core.building.Building;
import dev.hycolony.core.building.BuildingManager;
import dev.hycolony.core.building.BuildingModule;
import dev.hycolony.core.building.CreatesResolvers;
import dev.hycolony.core.job.WorkerModule;
import dev.hycolony.core.request.Resolver;
import java.util.ArrayList;
import java.util.List;

/** Keeps a colony's requests, work orders and workers in step with the buildings it gains or loses. */
final class ColonyBuildingListener implements BuildingManager.Listener {
    private final Colony colony;

    ColonyBuildingListener(Colony colony) {
        this.colony = colony;
    }

    @Override
    public void added(Building building) {
        List<Resolver> extra = new ArrayList<>();
        for (BuildingModule module : building.modules().values()) {
            if (module instanceof CreatesResolvers creator) {
                extra.addAll(creator.createResolvers(colony, building));
            }
        }
        building.attachResolvers(colony.context().ports().containers(), extra);
        colony.requests().onProviderAdded(building);
    }

    @Override
    public void removed(Building building) {
        colony.requests().cancelAllFrom(building.requesterId());
        colony.requests().onProviderRemoved(building);
        colony.work().onBuildingRemoved(building.position());
        for (BuildingModule module : building.modules().values()) {
            if (module instanceof WorkerModule worker) {
                // Snapshot: fire() mutates worker.workers(), which this would otherwise iterate live.
                for (int citizenId : List.copyOf(worker.workers())) {
                    worker.fire(colony, building, citizenId);
                }
            }
        }
    }
}
