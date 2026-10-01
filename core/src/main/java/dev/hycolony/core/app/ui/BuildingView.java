package dev.hycolony.core.app.ui;

import dev.hycolony.core.building.module.ModuleTab;
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
 * empty for a building that employs no one;
 * {@code pickupPriority}: shown on worker huts only (MC AbstractWindowWorkerModuleBuilding); {@code stock}: what the
 * hut and its racks hold, most first (MC WindowHutAllInventory); {@code tabs}: the tabs of the hut's modules, in
 * module order (MC module views); {@code upgradeWarning}: the language key of what the next level lacks, shown on
 * the build button (MC getHoverWarningForLevel).
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
        OptionalInt pickupPriority,
        List<ItemAmount> stock,
        List<ModuleTab> tabs,
        Optional<String> upgradeWarning) {
    /**
     * A worker or a candidate, with where it lives (MC WindowHireWorker's distance label); {@code homeDistance} in
     * blocks, for {@link HomeLine#DISTANCE} only.
     */
    public record WorkerRow(int citizenId, String name, HomeLine home, int homeDistance) {}

    /** MC hiring labels: homeless, lives here, lives at its current workplace, lives N blocks from here. */
    public enum HomeLine {
        HOMELESS,
        LIVES_HERE,
        LIVES_AT_WORK,
        DISTANCE
    }

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
