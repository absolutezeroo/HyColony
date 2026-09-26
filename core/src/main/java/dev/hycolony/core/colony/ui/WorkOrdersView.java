package dev.hycolony.core.colony.ui;

import dev.hycolony.core.construction.workorder.WorkOrderType;
import java.util.List;
import java.util.Optional;

/** The town hall's work order list, in execution order. */
public record WorkOrdersView(int colonyId, List<OrderLine> orders, boolean canManage) {
    public record OrderLine(
            int id,
            WorkOrderType type,
            String buildingName,
            int targetLevel,
            int priority,
            Optional<String> builderName) {}

    public WorkOrdersView {
        orders = List.copyOf(orders);
    }
}
