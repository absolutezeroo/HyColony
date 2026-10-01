package dev.hycolony.core.crafting.task;

import dev.hycolony.core.building.Building;
import dev.hycolony.core.building.module.ModuleTab;
import dev.hycolony.core.building.module.ProvidesTab;
import dev.hycolony.core.citizen.CitizenData;
import dev.hycolony.core.colony.Colony;
import dev.hycolony.core.job.WorkerModule;
import dev.hycolony.core.logistics.warehouse.TaskRows;
import dev.hycolony.core.request.model.RequestToken;
import java.util.List;
import java.util.UUID;

/**
 * A crafting hut's task list: a view-only module without state (MC {@code CRAFT_TASK_VIEW}, whose producer has no
 * module, only a {@code CrafterRequestTaskModuleView}).
 */
public final class CrafterTaskListModule implements ProvidesTab {
    /** MC CrafterRequestTaskModuleView.getTasks: the task queue of each worker of the hut who is a crafter. */
    @Override
    public ModuleTab tab(Colony colony, Building building, UUID viewer) {
        List<RequestToken> queue =
                building.module(WorkerModule.class).map(WorkerModule::workers).orElse(List.of()).stream()
                        .flatMap(id -> colony.citizens().get(id).flatMap(CitizenData::job).stream())
                        .filter(Crafter.class::isInstance)
                        .flatMap(j -> ((Crafter) j).craftingTasks().taskQueue().stream())
                        .toList();
        return new CrafterTasksView(TaskRows.of(colony, queue));
    }
}
