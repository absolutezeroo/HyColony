package dev.hycolony.core.colony.ui;

import dev.hycolony.core.colony.ui.logistics.CourierTabs;
import dev.hycolony.core.colony.ui.logistics.WarehouseTabs;
import dev.hycolony.core.construction.workorder.WorkOrderType;
import dev.hycolony.core.job.HiringMode;
import dev.hycolony.core.kernel.BlockPos;
import java.util.List;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.Set;

/**
 * A hut's window. {@code allowed} is empty while an order exists (the button becomes Cancel); {@code hiringMode} is
 * empty for a building that employs no one; {@code canPickUp}: deconstructed, MANAGE_HUTS, not the town hall;
 * {@code builder}: the builder hut's own tabs, empty for any other hut. {@code pickupPriority}: shown on worker huts
 * only (MC AbstractWindowWorkerModuleBuilding); {@code warehouse} and {@code courier}: those huts' own tabs.
 */
public record BuildingView(
        int colonyId,
        BlockPos pos,
        String typeId,
        int level,
        int maxLevel,
        boolean built,
        boolean deconstructed,
        List<WorkerRow> workers,
        List<WorkerRow> hireable,
        Optional<HiringMode> hiringMode,
        Optional<OrderRow> order,
        Set<WorkOrderType> allowed,
        List<String> styles,
        String style,
        boolean canManage,
        boolean canPickUp,
        Optional<BuilderTabs> builder,
        OptionalInt pickupPriority,
        Optional<WarehouseTabs> warehouse,
        Optional<CourierTabs> courier) {
    public record WorkerRow(int citizenId, String name) {}

    public record OrderRow(int id, WorkOrderType type, int targetLevel, Optional<String> builderName, int percent) {}

    public BuildingView {
        workers = List.copyOf(workers);
        hireable = List.copyOf(hireable);
        allowed = Set.copyOf(allowed);
        styles = List.copyOf(styles);
    }
}
