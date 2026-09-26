package dev.hycolony.core.colony.view;

import dev.hycolony.core.building.Building;
import dev.hycolony.core.colony.Colony;
import dev.hycolony.core.colony.permission.Action;
import dev.hycolony.core.colony.ui.WorkOrdersView;
import java.util.List;
import java.util.UUID;

/** Builds the town hall's work order list (MC WindowInfoPage.fillWorkOrderList), by priority. */
final class WorkOrderViews {
    private WorkOrderViews() {}

    static WorkOrdersView of(Colony c, UUID viewer) {
        List<WorkOrdersView.OrderLine> lines = c.work().ordered().stream()
                .map(o -> new WorkOrdersView.OrderLine(
                        o.id(),
                        o.type(),
                        c.buildings()
                                .at(o.buildingPos())
                                .map(Building::displayName)
                                .orElse(""),
                        o.targetLevel(),
                        WorkOrderStatus.builderName(c, o)))
                .toList();
        return new WorkOrdersView(lines, c.permissions().hasPermission(viewer, Action.MANAGE_HUTS));
    }
}
