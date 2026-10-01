package dev.hycolony.core.crafting.task;

import dev.hycolony.core.building.module.ModuleTab;
import dev.hycolony.core.logistics.warehouse.TaskRow;
import java.util.List;

/** A crafting hut's Tasks tab (MC CrafterRequestTaskModuleView): its crafters' task queues, head first. */
public record CrafterTasksView(List<TaskRow> tasks) implements ModuleTab {
    public CrafterTasksView {
        tasks = List.copyOf(tasks);
    }
}
