package dev.hycolony.core.logistics.warehouse;

import dev.hycolony.core.building.Building;
import dev.hycolony.core.citizen.CitizenData;
import dev.hycolony.core.colony.Colony;
import dev.hycolony.core.job.Job;
import dev.hycolony.core.kernel.BlockPos;
import dev.hycolony.core.request.Request;
import dev.hycolony.core.request.RequestManager;
import dev.hycolony.core.request.Resolver;
import dev.hycolony.core.request.model.Delivery;
import dev.hycolony.core.request.model.Pickup;
import dev.hycolony.core.request.model.RequestToken;
import dev.hycolony.core.request.model.Requestable;
import dev.hycolony.core.request.model.RequesterId;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Hands a warehouse's deliveries or pickups to its couriers (MC {@code DeliverymenRequestResolver}, with
 * {@code DeliveryRequestResolver} and {@code PickupRequestResolver}): it takes one while the warehouse has couriers
 * and queues it at the warehouse, where a courier pulls it; the request stays in progress until the courier is done.
 */
final class CourierResolver implements Resolver {
    /** MC {@code CONST_DEFAULT_RESOLVER_PRIORITY}: after the warehouse's stock (150), before retrying (50). */
    static final int PRIORITY = 100;

    private final Colony colony;
    private final Building warehouse;
    private final Class<? extends Requestable> type;
    private final String id;
    private final RequesterId requesterId;

    private CourierResolver(Colony colony, Building warehouse, Class<? extends Requestable> type, String kind) {
        this.colony = colony;
        this.warehouse = warehouse;
        this.type = type;
        this.id = kind + ":" + warehouse.requesterId().value();
        this.requesterId = new RequesterId("resolver:" + id);
    }

    /** MC {@code DeliveryRequestResolver}: the warehouse's resolver for {@link Delivery}. */
    static CourierResolver deliveries(Colony colony, Building warehouse) {
        return new CourierResolver(colony, warehouse, Delivery.class, "delivery");
    }

    /** MC {@code PickupRequestResolver}: the warehouse's resolver for {@link Pickup}. */
    static CourierResolver pickups(Colony colony, Building warehouse) {
        return new CourierResolver(colony, warehouse, Pickup.class, "pickup");
    }

    @Override
    public String resolverId() {
        return id;
    }

    @Override
    public int priority() {
        return PRIORITY;
    }

    @Override
    public boolean handles(Requestable requestable) {
        return type.isInstance(requestable);
    }

    /** MC {@code canResolveRequest}: never for another warehouse's request; otherwise whether this one has couriers. */
    @Override
    public boolean canResolve(RequestManager m, Request r) {
        return !fromAnotherWarehouse(r) && hasCouriers();
    }

    /** MC {@code attemptResolveRequest}: no children; empty without couriers. */
    @Override
    public Optional<List<Requestable>> attemptResolve(RequestManager m, Request r) {
        return hasCouriers() ? Optional.of(List.of()) : Optional.empty();
    }

    /** MC {@code resolveRequest}: appends the request to the warehouse queue, if the warehouse still has couriers. */
    @Override
    public void resolve(RequestManager m, Request r) {
        if (hasCouriers()) {
            queue().add(r.token());
            colony.markDirty();
        }
    }

    /**
     * MC {@code getSuitabilityMetric}: for a delivery {@code max(distance / 10, 1)} plus the warehouse queue length,
     * for a pickup the distance alone, from the requester to the warehouse. Deviation from MC: an unknown requester
     * (its hut removed) scores the worst; MC's requester keeps its saved location, so it always gets a distance (or
     * NPEs).
     */
    @Override
    public double suitability(RequestManager m, Request r) {
        Optional<BlockPos> from = RequesterLocation.of(colony, r.requester());
        if (from.isEmpty()) {
            return Double.MAX_VALUE;
        }
        int distance = (int) Math.sqrt((double) from.get().distSq(warehouse.position()));
        return type == Pickup.class ? distance : Math.max(distance / 10, 1) + queue().size();
    }

    /**
     * MC {@code onAssignedRequestCancelled}: drops the request from the first courier queue holding it, then from
     * the warehouse queue. A failed request goes through here too, as MC treats it as cancelled.
     */
    @Override
    public void onCancelled(RequestManager m, Request r) {
        for (CitizenData citizen : colony.citizens().all()) {
            Job job = citizen.job().orElse(null);
            if (job instanceof CourierTaskQueue courier && courier.removeTask(r.token())) {
                break;
            }
        }
        queue().remove(r.token());
        colony.markDirty();
    }

    /** The requester sits at a warehouse other than this one: its couriers carry it. */
    private boolean fromAnotherWarehouse(Request r) {
        return RequesterLocation.of(colony, r.requester())
                .filter(pos -> !pos.equals(warehouse.position()))
                .flatMap(colony.buildings()::at)
                .map(CourierResolver::isWarehouse)
                .orElse(false);
    }

    /** MC {@code hasCouriers}: the warehouse still stands and has at least one courier attached. */
    private boolean hasCouriers() {
        return colony.buildings()
                        .at(warehouse.position())
                        .map(CourierResolver::isWarehouse)
                        .orElse(false)
                && warehouse
                        .module(CourierAssignmentModule.class)
                        .map(c -> !c.couriers().isEmpty())
                        .orElse(false);
    }

    private static boolean isWarehouse(Building building) {
        return WarehouseBuilding.TYPE_ID.equals(building.type().id());
    }

    private List<RequestToken> queue() {
        return warehouse
                .module(WarehouseRequestQueue.class)
                .map(WarehouseRequestQueue::tokens)
                .orElseGet(ArrayList::new);
    }

    @Override
    public RequesterId requesterId() {
        return requesterId;
    }

    @Override
    public BlockPos location() {
        return warehouse.position();
    }

    @Override
    public String displayName() {
        return warehouse.displayName();
    }

    @Override
    public void onRequestComplete(RequestManager manager, Request request) {}

    @Override
    public void onRequestCancelled(RequestManager manager, Request request) {}
}
