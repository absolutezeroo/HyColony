package dev.hycolony.core.logistics.courier;

import dev.hycolony.core.building.module.ModuleTab;
import dev.hycolony.core.kernel.BlockPos;
import dev.hycolony.core.logistics.warehouse.TaskRow;
import java.util.List;
import java.util.Optional;

/**
 * The courier hut's task list (MC CourierRequestTaskModuleView): its courier's own queue, head first, and the
 * warehouse that courier is attached to; empty when there is none, so the player sees why nothing moves.
 */
public record CourierTasksView(Optional<BlockPos> warehouse, List<TaskRow> tasks) implements ModuleTab {
    public CourierTasksView {
        tasks = List.copyOf(tasks);
    }
}
