package dev.hycolony.core.logistics.courier;

import dev.hycolony.core.building.Building;
import dev.hycolony.core.building.module.ModuleTab;
import dev.hycolony.core.building.module.ProvidesTab;
import dev.hycolony.core.citizen.CitizenData;
import dev.hycolony.core.citizen.food.EatingRule;
import dev.hycolony.core.colony.Colony;
import dev.hycolony.core.job.WorkerModule;
import dev.hycolony.core.kernel.item.ItemKey;
import dev.hycolony.core.logistics.warehouse.CourierAssignmentModule;
import dev.hycolony.core.logistics.warehouse.TaskRows;
import dev.hycolony.core.request.model.Delivery;
import dev.hycolony.core.request.model.RequestToken;
import java.util.List;
import java.util.UUID;

/**
 * The courier hut's task list: a view-only module without state (MC {@code COURIER_TASK_VIEW}, whose producer has no
 * module, only a {@code CourierRequestTaskModuleView}). It also keeps the courier from eating what it delivers.
 */
final class CourierTaskListModule implements ProvidesTab, EatingRule {
    /** MC BuildingDeliveryman.canEat: the first courier never eats the item of the delivery it is on. */
    @Override
    public boolean canEat(Colony colony, Building hut, ItemKey food) {
        return WorkerModule.firstWorker(colony, hut)
                .flatMap(CitizenData::job)
                .filter(DeliverymanJob.class::isInstance)
                .flatMap(j -> ((DeliverymanJob) j).currentTask(colony))
                .map(task -> !(task.requestable() instanceof Delivery d
                        && d.stack().item().equals(food)))
                .orElse(true);
    }

    /**
     * MC CourierRequestTaskModuleView: the queues of the hut's courier workers, head first, and the warehouse the first
     * one is attached to.
     */
    @Override
    public ModuleTab tab(Colony colony, Building building, UUID viewer) {
        List<Integer> workers =
                building.module(WorkerModule.class).map(WorkerModule::workers).orElse(List.of());
        List<RequestToken> queue = workers.stream()
                .flatMap(id -> colony.citizens().get(id).flatMap(CitizenData::job).stream())
                .filter(DeliverymanJob.class::isInstance)
                .flatMap(j -> ((DeliverymanJob) j).taskQueue().stream())
                .toList();
        return new CourierTasksView(
                workers.stream()
                        .findFirst()
                        .flatMap(id -> CourierAssignmentModule.warehouseOf(colony, id))
                        .map(Building::position),
                TaskRows.of(colony, queue));
    }
}
