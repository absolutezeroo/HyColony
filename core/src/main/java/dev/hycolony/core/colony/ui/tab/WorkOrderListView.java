package dev.hycolony.core.colony.ui.tab;

import dev.hycolony.core.construction.workorder.ManualSelection;
import dev.hycolony.core.construction.workorder.WorkOrderType;
import java.util.List;
import java.util.Optional;

/**
 * The builder hut's Work orders tab (MC WorkOrderListModuleView, shown by WorkOrderModuleWindow): the orders it could
 * take; {@code manual} when the hut is in MANUAL mode, where the player selects them.
 */
public record WorkOrderListView(List<OrderLine> orders, boolean manual) implements ModuleTab {
    /**
     * A WorkOrderModuleWindow row: {@code distance} in blocks (|dx| + |dz|), {@code current} the order the builder
     * works on (green frame), {@code claimedHere} shows Cancel; otherwise, in MANUAL mode, Select is enabled when
     * {@code selectRefusal} is empty.
     */
    public record OrderLine(
            int id,
            WorkOrderType type,
            String buildingName,
            int targetLevel,
            long distance,
            boolean current,
            boolean claimedHere,
            Optional<ManualSelection.Refusal> selectRefusal) {}

    public WorkOrderListView {
        orders = List.copyOf(orders);
    }
}
