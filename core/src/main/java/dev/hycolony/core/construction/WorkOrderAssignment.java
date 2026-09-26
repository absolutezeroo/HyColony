package dev.hycolony.core.construction;

import dev.hycolony.core.building.Building;
import dev.hycolony.core.colony.Colony;
import dev.hycolony.core.kernel.BlockPos;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * WorkManager.onColonyTick's assignment: each free order, in {@link WorkManager#ordered()} order, goes to the first
 * builder hut (in building order) that has a worker, holds no order, is not MANUAL and passes
 * {@link WorkManager#canBuild}. Claimed orders already sit with their builder (MineColonies sorts them first only to
 * mark those builders busy).
 */
final class WorkOrderAssignment {
    private final Colony colony;

    WorkOrderAssignment(Colony colony) {
        this.colony = colony;
    }

    void assign(Collection<WorkOrder> orders) {
        Set<BlockPos> busy = new HashSet<>();
        List<WorkOrder> free = new ArrayList<>();
        for (WorkOrder o : orders) {
            Optional<BlockPos> claimer = o.claimedBy();
            if (claimer.isPresent() && colony.buildings().at(claimer.get()).isPresent()) {
                busy.add(claimer.get());
                continue;
            }
            if (claimer.isPresent()) { // failsafe: the builder hut vanished
                o.release();
                colony.markDirty();
            }
            free.add(o);
        }
        if (free.isEmpty()) {
            return;
        }
        free.sort(WorkManager.ORDER);
        List<Building> idle = idleBuilders(busy);
        for (WorkOrder o : free) {
            claimByFirstFitting(o, idle);
        }
    }

    private List<Building> idleBuilders(Set<BlockPos> busy) {
        List<Building> idle = new ArrayList<>();
        for (Building b : colony.buildings().all()) {
            if (WorkManager.isEmployedBuilder(b)
                    && !busy.contains(b.position())
                    && b.module(BuilderSettingsModule.class)
                            .map(s -> s.mode() != BuilderSettingsModule.Mode.MANUAL)
                            .orElse(true)) {
                idle.add(b);
            }
        }
        return idle;
    }

    /** The first idle builder that can build {@code o} claims it and is no longer idle. */
    private void claimByFirstFitting(WorkOrder o, List<Building> idle) {
        for (Iterator<Building> it = idle.iterator(); it.hasNext(); ) {
            Building b = it.next();
            if (WorkManager.canBuild(b, o, b.level())) {
                o.setClaimedBy(b.position());
                it.remove();
                colony.markDirty();
                return;
            }
        }
    }
}
