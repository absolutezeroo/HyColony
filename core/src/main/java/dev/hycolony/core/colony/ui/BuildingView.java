package dev.hycolony.core.colony.ui;

import dev.hycolony.core.colony.ui.tab.ModuleTab;
import dev.hycolony.core.construction.workorder.WorkOrderType;
import dev.hycolony.core.job.HiringMode;
import dev.hycolony.core.kernel.BlockPos;
import dev.hycolony.core.kernel.item.ItemAmount;
import java.util.List;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.Set;

/**
 * A hut's window. {@code allowed} is empty while an order exists (the button becomes Cancel); {@code hiringMode} is
 * empty for a building that employs no one; {@code canPickUp}: deconstructed, MANAGE_HUTS, not the town hall;
 * {@code pickupPriority}: shown on worker huts only (MC AbstractWindowWorkerModuleBuilding); {@code stock}: what the
 * hut and its racks hold, most first (MC WindowHutAllInventory); {@code tabs}: the tabs of the hut's modules, in
 * module order (MC module views).
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
        OptionalInt pickupPriority,
        List<ItemAmount> stock,
        List<ModuleTab> tabs) {
    public record WorkerRow(int citizenId, String name) {}

    public record OrderRow(int id, WorkOrderType type, int targetLevel, Optional<String> builderName, int percent) {}

    public BuildingView {
        workers = List.copyOf(workers);
        hireable = List.copyOf(hireable);
        allowed = Set.copyOf(allowed);
        styles = List.copyOf(styles);
        stock = List.copyOf(stock);
        tabs = List.copyOf(tabs);
    }

    /** The first module tab of kind {@code kind}; empty when the hut has none. */
    public <T extends ModuleTab> Optional<T> tab(Class<T> kind) {
        return tabs.stream().filter(kind::isInstance).map(kind::cast).findFirst();
    }
}
