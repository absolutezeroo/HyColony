package dev.hycolony.core.colony.view;

import dev.hycolony.core.building.Building;
import dev.hycolony.core.citizen.CitizenData;
import dev.hycolony.core.colony.Colony;
import dev.hycolony.core.colony.ColonyContext;
import dev.hycolony.core.colony.ui.logistics.CourierTabs;
import dev.hycolony.core.colony.ui.logistics.TaskRow;
import dev.hycolony.core.colony.ui.logistics.WarehouseTabs;
import dev.hycolony.core.job.WorkerModule;
import dev.hycolony.core.kernel.BlockPos;
import dev.hycolony.core.kernel.item.ItemAmount;
import dev.hycolony.core.logistics.courier.DeliverymanJob;
import dev.hycolony.core.logistics.warehouse.CourierAssignmentModule;
import dev.hycolony.core.logistics.warehouse.RequesterLocation;
import dev.hycolony.core.logistics.warehouse.WarehouseRequestQueue;
import dev.hycolony.core.request.Request;
import dev.hycolony.core.request.model.Deliverable;
import dev.hycolony.core.request.model.Delivery;
import dev.hycolony.core.request.model.Pickup;
import dev.hycolony.core.request.model.RequestState;
import dev.hycolony.core.request.model.RequestToken;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.OptionalInt;

/** Builds the logistics parts of a hut's window: pickup priority, the warehouse's tabs and the courier's task list. */
final class LogisticsViews {
    private final ColonyContext ctx;

    LogisticsViews(ColonyContext ctx) {
        this.ctx = ctx;
    }

    /** MC AbstractWindowWorkerModuleBuilding: only a hut with workers shows its pickup priority; empty otherwise. */
    static OptionalInt pickupPriority(Building b) {
        return b.module(WorkerModule.class).isPresent()
                ? OptionalInt.of(b.pickupPriority().value())
                : OptionalInt.empty();
    }

    /** The warehouse's couriers, stock and waiting tasks; empty for a hut without couriers to attach. */
    Optional<WarehouseTabs> warehouse(Colony c, Building b) {
        return b.module(CourierAssignmentModule.class)
                .map(m -> new WarehouseTabs(
                        m.couriers().stream()
                                .flatMap(id -> c.citizens().get(id).stream())
                                .map(CitizenData::name)
                                .toList(),
                        CourierAssignmentModule.maxCouriers(b),
                        stock(b),
                        tasks(
                                c,
                                b.module(WarehouseRequestQueue.class)
                                        .map(WarehouseRequestQueue::tokens)
                                        .orElse(List.of()))));
    }

    /**
     * MC WindowHutAllInventory with its "count, descending" sort: every item of the hut block and racks, most held
     * first, ties by id.
     *
     * <p>Deviation from MC: no sort button nor search field; the list always uses this order.
     */
    private List<ItemAmount> stock(Building b) {
        return ctx.ports().containers().contents(b.containers()).entrySet().stream()
                .filter(e -> e.getValue() > 0)
                .map(e -> new ItemAmount(e.getKey(), e.getValue()))
                .sorted(Comparator.comparingInt(ItemAmount::count)
                        .reversed()
                        .thenComparing(a -> a.item().id()))
                .toList();
    }

    /**
     * MC CourierRequestTaskModuleView: the queues of the hut's courier workers, head first, and the warehouse the
     * first one is attached to; empty for a hut that employs no courier.
     */
    static Optional<CourierTabs> courier(Colony c, Building b) {
        Optional<WorkerModule> w =
                b.module(WorkerModule.class).filter(m -> m.job().equals(DeliverymanJob.TYPE));
        if (w.isEmpty()) {
            return Optional.empty();
        }
        List<Integer> workers = w.get().workers();
        List<RequestToken> queue = workers.stream()
                .flatMap(id -> c.citizens().get(id).flatMap(CitizenData::job).stream())
                .filter(DeliverymanJob.class::isInstance)
                .flatMap(j -> ((DeliverymanJob) j).taskQueue().stream())
                .toList();
        return Optional.of(new CourierTabs(
                workers.stream()
                        .findFirst()
                        .flatMap(id -> CourierAssignmentModule.warehouseOf(c, id))
                        .map(Building::position),
                tasks(c, queue)));
    }

    /** One row per token still known, in order (MC drops the tokens whose request is gone). */
    private static List<TaskRow> tasks(Colony c, List<RequestToken> tokens) {
        return tokens.stream()
                .flatMap(t -> c.requests().get(t).stream())
                .map(r -> new TaskRow(
                        r.token(),
                        r.requestable(),
                        RequestViews.requesterName(c, r),
                        forRequester(c, r),
                        priority(r),
                        r.state() == RequestState.IN_PROGRESS))
                .toList();
    }

    /**
     * MC WindowHutRequestTaskModule: climbs the parents while they ask from the same place as {@code r}, and names the
     * requester of the one reached; empty without a parent.
     */
    private static Optional<String> forRequester(Colony c, Request r) {
        Request parent = r.parent().flatMap(c.requests()::get).orElse(null);
        Optional<BlockPos> here = RequesterLocation.of(c, r.requester());
        while (parent != null
                && parent.parent().isPresent()
                && RequesterLocation.of(c, parent.requester()).equals(here)) {
            Request up = c.requests().get(parent.parent().get()).orElse(null);
            if (up == null) {
                break;
            }
            parent = up;
        }
        return Optional.ofNullable(parent).map(p -> RequestViews.requesterName(c, p));
    }

    /** MC IDeliverymanRequestable.getPriority; 0 for any other request. */
    private static int priority(Request r) {
        return switch (r.requestable()) {
            case Delivery d -> d.priority();
            case Pickup p -> p.priority();
            case Deliverable _ -> 0;
        };
    }
}
