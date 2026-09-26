package dev.hycolony.core.colony.ui;

import dev.hycolony.core.construction.workorder.WorkOrderType;
import java.util.List;
import java.util.Optional;

/**
 * The town hall Information tab's work order list (MC WindowInfoPage workOrderList), in execution order. MC shows no
 * priority number: the row order is the priority.
 */
public record WorkOrdersView(List<OrderLine> orders, boolean canManage) {
    public record OrderLine(
            int id, WorkOrderType type, String buildingName, int targetLevel, Optional<String> builderName) {}

    public WorkOrdersView {
        orders = List.copyOf(orders);
    }
}
