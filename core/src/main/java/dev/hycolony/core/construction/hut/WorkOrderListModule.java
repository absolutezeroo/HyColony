package dev.hycolony.core.construction.hut;

import dev.hycolony.core.building.Building;
import dev.hycolony.core.building.module.ModuleTab;
import dev.hycolony.core.building.module.ProvidesTab;
import dev.hycolony.core.colony.Colony;
import dev.hycolony.core.construction.resources.BuildingResourcesModule;
import dev.hycolony.core.construction.shared.BuilderSettingsModule;
import dev.hycolony.core.construction.workorder.ManualSelection;
import dev.hycolony.core.construction.workorder.WorkManager;
import dev.hycolony.core.construction.workorder.WorkOrder;
import dev.hycolony.core.kernel.BlockPos;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;

/**
 * The builder hut's Work orders tab: a view-only module without state (MC {@code WORKORDER_VIEW}, whose producer has
 * no module, only a {@code WorkOrderListModuleView}). It reads the hut's mode and current order from its other
 * modules, as MC WorkOrderModuleWindow reads them from the building view.
 */
final class WorkOrderListModule implements ProvidesTab {
    /**
     * WorkOrderModuleWindow.updateWorkOrders: the orders this hut could build (ignoring distance) that it claimed, or,
     * in MANUAL mode, that nobody claimed; the current one first, then those claimed here, then by priority.
     */
    @Override
    public ModuleTab tab(Colony colony, Building hut, UUID viewer) {
        BlockPos pos = hut.position();
        boolean manual = hut.module(BuilderSettingsModule.class)
                .filter(s -> s.mode() == BuilderSettingsModule.Mode.MANUAL)
                .isPresent();
        int currentId = hut.module(BuildingResourcesModule.class)
                .map(BuildingResourcesModule::orderId)
                .orElse(0);
        Comparator<WorkOrder> group =
                Comparator.comparingInt(o -> o.id() == currentId ? 0 : o.isClaimedBy(pos) ? 1 : 2);
        List<WorkOrderListView.OrderLine> lines = colony.work().ordered().stream() // already WORK_ORDER_COMPARATOR
                .filter(o -> WorkManager.canBuildIgnoringDistance(pos, hut.level(), o)
                        && (o.isClaimedBy(pos) || (manual && o.claimedBy().isEmpty())))
                .sorted(group) // stable: keeps the priority order within each group
                .map(o -> new WorkOrderListView.OrderLine(
                        o.id(),
                        o.type(),
                        colony.buildings()
                                .at(o.buildingPos())
                                .map(Building::displayName)
                                .orElse(""),
                        o.targetLevel(),
                        distance2d(pos, o.buildingPos()),
                        o.id() == currentId,
                        o.isClaimedBy(pos),
                        ManualSelection.check(hut, o)))
                .toList();
        return new WorkOrderListView(lines, manual);
    }

    /** BlockPosUtil.getDistance2D: |dx| + |dz|. */
    private static long distance2d(BlockPos a, BlockPos b) {
        return Math.abs((long) a.x() - b.x()) + Math.abs((long) a.z() - b.z());
    }
}
