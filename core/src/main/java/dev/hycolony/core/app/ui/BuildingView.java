package dev.hycolony.core.app.ui;

import dev.hycolony.core.app.hut.HireView;
import dev.hycolony.core.app.hut.HutStock;
import dev.hycolony.core.building.module.ModuleTab;
import dev.hycolony.core.construction.workorder.WorkOrderType;
import dev.hycolony.core.kernel.BlockPos;
import java.util.List;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.Set;

/**
 * A hut's window. {@code customName}: the name the players gave the hut, empty for none (the title then shows the
 * type's name, MC IBuildingView.getBuildingDisplayName). {@code hire}: the hire window of a hut that employs workers
 * (MC WindowHireWorker), empty otherwise. {@code allowed} is empty while an order exists (the button becomes Cancel);
 * {@code pickupPriority}: shown on worker huts only (MC AbstractWindowWorkerModuleBuilding); {@code stock}: what the
 * hut and its racks hold, by item with the containers holding it (MC WindowHutAllInventory); {@code tabs}: the tabs
 * of the hut's modules, in module order (MC module views); {@code upgradeWarning}: the language key of what the next
 * level lacks, shown on the build button (MC getHoverWarningForLevel).
 */
public record BuildingView(
        int colonyId,
        BlockPos pos,
        String typeId,
        int level,
        int maxLevel,
        boolean built,
        boolean deconstructed,
        String customName,
        MainKind mainKind,
        List<WorkerLine> workers,
        Optional<HireView> hire,
        Optional<OrderRow> order,
        Set<WorkOrderType> allowed,
        List<String> styles,
        String style,
        boolean canManage,
        OptionalInt pickupPriority,
        List<HutStock> stock,
        List<ModuleTab> tabs,
        Optional<String> upgradeWarning) {
    /** A worker on the main page (MC AbstractWindowWorkerModuleBuilding): "Job: Name", tooltip "Name (id)". */
    public record WorkerLine(int citizenId, String name, String jobId) {}

    /**
     * MC AbstractBuildingView.getWindow: a hut with workers (layouthutpageactions), a residence (windowhuthome) or
     * any other hut (layouthutpageactionsmin).
     */
    public enum MainKind {
        WORKERS,
        LIVING,
        SIMPLE
    }

    public record OrderRow(int id, WorkOrderType type, int targetLevel, Optional<String> builderName, int percent) {}

    public BuildingView {
        workers = List.copyOf(workers);
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
