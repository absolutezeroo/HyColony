package dev.hycolony.core.colony.ui.tab;

import java.util.List;

/** The warehouse's Couriers tab (MC CourierAssignmentModuleView): its couriers' names, at most {@code maxCouriers}. */
public record CourierAssignmentView(List<String> couriers, int maxCouriers) implements ModuleTab {
    public CourierAssignmentView {
        couriers = List.copyOf(couriers);
    }
}
