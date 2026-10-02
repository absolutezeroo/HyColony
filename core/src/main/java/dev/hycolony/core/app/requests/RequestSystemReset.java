package dev.hycolony.core.app.requests;

import dev.hycolony.core.citizen.CitizenData;
import dev.hycolony.core.colony.Colony;
import dev.hycolony.core.crafting.task.Crafter;
import dev.hycolony.core.crafting.task.CraftingTasks;
import dev.hycolony.core.logistics.courier.DeliverymanJob;
import dev.hycolony.core.request.model.RequestToken;
import dev.hycolony.core.request.resolver.PlayerResolver;
import dev.hycolony.core.request.resolver.RetryingResolver;
import java.util.ArrayList;
import java.util.List;

/**
 * MC /mc colony requestsystem-reset (StandardRequestManager.reset, then InitialUpdate): a colony's request system
 * started afresh, its workers asking again at their next need (docs/research/request-system-reset.md).
 *
 * <p>Deviation from MC: the huts' resolvers are registered again as they are (they keep no state), where MC creates
 * new ones (AbstractBuilding.createResolvers); and no api event tells of the reset, MC having none.
 */
public final class RequestSystemReset {
    private RequestSystemReset() {}

    /**
     * Forgets every request and assignment of {@code c} without a cancel callback, registers new player and retrying
     * resolvers and every hut again, empties the crafters' tasks and the couriers' queues and deliveries (MC keeps them
     * in the stores it renews), all in one step of the request manager, and saves. The warehouse queue and the
     * crafters' counters stay, as in MC.
     */
    public static void reset(Colony c) {
        c.requests()
                .reset(
                        List.of(new PlayerResolver(c.center()), new RetryingResolver(c.center())),
                        c.buildings().all(),
                        () -> forgetJobTasks(c));
        c.markDirty();
    }

    /** Empties every crafter's and courier's tasks, which MC keeps in the stores the reset renews. */
    private static void forgetJobTasks(Colony c) {
        for (CitizenData d : c.citizens().all()) {
            switch (d.job().orElse(null)) {
                case Crafter crafter -> forget(crafter.craftingTasks());
                case DeliverymanJob courier -> forget(courier);
                case null, default -> {}
            }
        }
    }

    private static void forget(CraftingTasks tasks) {
        List<RequestToken> all = new ArrayList<>(tasks.taskQueue());
        all.addAll(tasks.assignedTasks());
        all.forEach(tasks::onTaskDeletion);
    }

    private static void forget(DeliverymanJob courier) {
        new ArrayList<>(courier.taskQueue()).forEach(courier::removeTask);
        new ArrayList<>(courier.ongoingDeliveries()).forEach(courier::removeConcurrentDelivery);
    }
}
