package dev.hycolony.core.colony.ui.logistics;

import dev.hycolony.core.kernel.item.ItemAmount;
import java.util.List;

/**
 * The warehouse's own tabs: its couriers (MC CourierAssignmentModuleView, at most {@code maxCouriers}), its stock (MC
 * WindowHutAllInventory, most held first) and the tasks waiting for a courier (MC WarehouseRequestTaskModuleView).
 */
public record WarehouseTabs(List<String> couriers, int maxCouriers, List<ItemAmount> stock, List<TaskRow> queue) {
    public WarehouseTabs {
        couriers = List.copyOf(couriers);
        stock = List.copyOf(stock);
        queue = List.copyOf(queue);
    }
}
