package dev.hycolony.core.colony.view;

import dev.hycolony.core.building.Building;
import dev.hycolony.core.citizen.CitizenData;
import dev.hycolony.core.colony.Colony;
import dev.hycolony.core.construction.resources.BuildingResourcesModule;
import dev.hycolony.core.construction.workorder.WorkOrder;
import dev.hycolony.core.job.WorkerModule;
import java.util.Optional;

/** A work order's live status, read from the builder hut that claimed it: who builds it, and how far it got. */
final class WorkOrderStatus {
    private WorkOrderStatus() {}

    static Optional<String> builderName(Colony c, WorkOrder o) {
        return o.claimedBy()
                .flatMap(c.buildings()::at)
                .flatMap(hut -> firstWorker(c, hut))
                .map(CitizenData::name);
    }

    /** The order's progress, from its builder's resources module once that builder started it; 0 before. */
    static int percent(Colony c, WorkOrder o) {
        return o.claimedBy()
                .flatMap(c.buildings()::at)
                .flatMap(hut -> hut.module(BuildingResourcesModule.class))
                .filter(m -> m.orderId() == o.id())
                .map(m -> m.needs().progressPercent())
                .orElse(0);
    }

    private static Optional<CitizenData> firstWorker(Colony c, Building b) {
        return b.module(WorkerModule.class)
                .flatMap(w -> w.workers().stream().findFirst())
                .flatMap(c.citizens()::get);
    }
}
