package dev.hycolony.core.logistics.warehouse;

import dev.hycolony.core.building.module.ModuleTab;
import java.util.List;

/** The warehouse's Tasks tab (MC WarehouseRequestTaskModuleView): the tasks waiting for a courier, oldest first. */
public record WarehouseTasksView(List<TaskRow> queue) implements ModuleTab {
    public WarehouseTasksView {
        queue = List.copyOf(queue);
    }
}
