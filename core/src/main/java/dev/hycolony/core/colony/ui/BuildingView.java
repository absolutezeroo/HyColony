package dev.hycolony.core.colony.ui;

import dev.hycolony.core.construction.WorkOrderType;
import dev.hycolony.core.job.HiringMode;
import dev.hycolony.core.kernel.BlockPos;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * A hut's window. {@code allowed} is empty while an order exists (the button becomes Cancel); {@code hiringMode} is
 * empty for a building that employs no one; {@code canPickUp}: deconstructed, MANAGE_HUTS, not the town hall.
 */
public record BuildingView(int colonyId, BlockPos pos, String typeId, int level, int maxLevel, boolean built,
                           boolean deconstructed, List<WorkerRow> workers, List<WorkerRow> hireable,
                           Optional<HiringMode> hiringMode,
                           Optional<OrderRow> order, Set<WorkOrderType> allowed, List<String> styles, String style,
                           boolean canManage, boolean canPickUp) {
    public record WorkerRow(int citizenId, String name) {}

    public record OrderRow(int id, WorkOrderType type, int targetLevel, Optional<String> builderName, int percent) {}

    public BuildingView {
        workers = List.copyOf(workers);
        hireable = List.copyOf(hireable);
        allowed = Set.copyOf(allowed);
        styles = List.copyOf(styles);
    }
}
