package dev.hycolony.core.construction.resources;

import dev.hycolony.core.building.Building;
import dev.hycolony.core.colony.Colony;
import dev.hycolony.core.colony.ui.tab.BuilderTabs;
import dev.hycolony.core.construction.shared.BuilderSettingsModule;
import dev.hycolony.core.construction.workorder.ManualSelection;
import dev.hycolony.core.construction.workorder.WorkManager;
import dev.hycolony.core.construction.workorder.WorkOrder;
import dev.hycolony.core.kernel.BlockPos;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;

/** Builds the builder hut's own tabs: Resources, Settings (its mode) and Work orders (MC WorkOrderModuleWindow). */
final class BuilderTabsViews {
    private BuilderTabsViews() {}

    /** The tabs of {@code hut}, whose resources module is {@code m}; a hut without settings shows the default mode. */
    static BuilderTabs of(Colony c, Building hut, BuildingResourcesModule m, UUID viewer) {
        BuilderSettingsModule.Mode mode = hut.module(BuilderSettingsModule.class)
                .map(BuilderSettingsModule::mode)
                .orElse(BuilderSettingsModule.Mode.AUTO);
        return new BuilderTabs(BuilderResourcesViews.of(c, hut, m, viewer), mode, orders(c, hut, m.orderId(), mode));
    }

    /**
     * WorkOrderModuleWindow.updateWorkOrders: the orders this hut could build (ignoring distance) that it claimed, or,
     * in MANUAL mode, that nobody claimed; the current one first, then those claimed here, then by priority.
     */
    private static List<BuilderTabs.OrderLine> orders(
            Colony c, Building hut, int currentId, BuilderSettingsModule.Mode mode) {
        BlockPos pos = hut.position();
        boolean manual = mode == BuilderSettingsModule.Mode.MANUAL;
        Comparator<WorkOrder> group =
                Comparator.comparingInt(o -> o.id() == currentId ? 0 : o.isClaimedBy(pos) ? 1 : 2);
        return c.work().ordered().stream() // already WORK_ORDER_COMPARATOR: the stable sort below keeps it per group
                .filter(o -> WorkManager.canBuildIgnoringDistance(pos, hut.level(), o)
                        && (o.isClaimedBy(pos) || (manual && o.claimedBy().isEmpty())))
                .sorted(group)
                .map(o -> new BuilderTabs.OrderLine(
                        o.id(),
                        o.type(),
                        c.buildings()
                                .at(o.buildingPos())
                                .map(Building::displayName)
                                .orElse(""),
                        o.targetLevel(),
                        distance2d(pos, o.buildingPos()),
                        o.id() == currentId,
                        o.isClaimedBy(pos),
                        ManualSelection.check(hut, o)))
                .toList();
    }

    /** BlockPosUtil.getDistance2D: |dx| + |dz|. */
    private static long distance2d(BlockPos a, BlockPos b) {
        return Math.abs((long) a.x() - b.x()) + Math.abs((long) a.z() - b.z());
    }
}
