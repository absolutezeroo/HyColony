package dev.hycolony.core.logistics.warehouse;

import dev.hycolony.core.building.Building;
import dev.hycolony.core.colony.Colony;
import dev.hycolony.core.kernel.BlockPos;
import dev.hycolony.core.kernel.item.ItemAmount;
import dev.hycolony.core.kernel.port.ContainerAccess;
import dev.hycolony.core.request.Request;
import dev.hycolony.core.request.RequestManager;
import dev.hycolony.core.request.Resolver;
import dev.hycolony.core.request.model.Deliverable;
import dev.hycolony.core.request.model.Delivery;
import dev.hycolony.core.request.model.RequestState;
import dev.hycolony.core.request.model.Requestable;
import dev.hycolony.core.request.model.RequesterId;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Serves item requests from a warehouse's racks (MC {@code AbstractWarehouseRequestResolver}): it takes a request
 * when all the colony's warehouses together hold enough, asks for the missing part as a child, and on completion
 * hands the items over as one {@link Delivery} per source slot, which the couriers carry.
 *
 * <p>Deviation from MC: one resolver instead of {@code WarehouseRequestResolver} and
 * {@code WarehouseConcreteRequestResolver}. MC splits them only to count concrete stacks (NBT, damage) apart from
 * predicates; {@link Deliverable#matches} covers both here. Our {@code StackList} carries no {@code leftOver} (MC
 * {@code INonExhaustiveDeliverable}), so the {@code leftOver} MC keeps is always 0, and we have no
 * {@code MinimumStack}, so the "not for another warehouse's minimum stock" rule has nothing to test.
 */
final class WarehouseStockResolver implements Resolver {
    /** MC {@code CONST_WAREHOUSE_RESOLVER_PRIORITY}: after the hut's own stock (200), before the couriers (100). */
    static final int PRIORITY = 150;

    private final Colony colony;
    private final Building warehouse;
    private final String id;
    private final RequesterId requesterId;

    WarehouseStockResolver(Colony colony, Building warehouse) {
        this.colony = colony;
        this.warehouse = warehouse;
        this.id = "warehouse:" + warehouse.requesterId().value();
        this.requesterId = new RequesterId("resolver:" + id);
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
        return requestable instanceof Deliverable;
    }

    /**
     * MC {@code canResolveRequest}: never for a requester at this warehouse (its own children included), nor
     * without stock here; then true once all warehouses hold {@code count}, else whether they hold {@code minCount}.
     */
    @Override
    public boolean canResolve(RequestManager m, Request r) {
        return !isAtThisWarehouse(r)
                && r.deliverable().map(d -> warehousesHold(d, m)).orElse(false);
    }

    /** Whether this warehouse has some of {@code d} and all of them together enough (see {@link #canResolve}). */
    private boolean warehousesHold(Deliverable d, RequestManager m) {
        int total = count(warehouse, d, m);
        if (total <= 0) {
            return false;
        }
        for (Building other : colony.buildings().all()) {
            if (!other.position().equals(warehouse.position())
                    && WarehouseBuilding.TYPE_ID.equals(other.type().id())) {
                total += count(other, d, m);
                if (total >= d.count()) {
                    return true;
                }
            }
        }
        return total >= d.minCount();
    }

    /** MC {@code attemptResolveRequest}: no child when this warehouse holds enough, else one for the missing count. */
    @Override
    public Optional<List<Requestable>> attemptResolve(RequestManager m, Request r) {
        Deliverable d = r.deliverable().orElseThrow(); // canResolve took only deliverables
        int available = 0;
        for (RackStack s : matchingStacks(d, m)) {
            available += s.stack().count();
        }
        if (available >= d.count() || available >= d.minCount()) {
            return Optional.of(List.of());
        }
        return Optional.of(List.of(d.withCount(d.count() - available)));
    }

    @Override
    public void resolve(RequestManager m, Request r) {
        m.updateState(r.token(), RequestState.RESOLVED);
    }

    /**
     * MC {@code getFollowupRequestForCompletion}: records the items as {@code r}'s deliveries and returns one
     * {@link Delivery} per matching slot, rack to requester, until {@code count} is covered; none if the stock is gone.
     */
    @Override
    public List<Requestable> followups(RequestManager m, Request r) {
        Deliverable d = r.deliverable().orElseThrow();
        List<Requestable> deliveries = new ArrayList<>();
        int remaining = d.count();
        for (RackStack s : matchingStacks(d, m)) {
            ItemAmount part = s.stack().withCount(Math.min(remaining, s.stack().count()));
            m.addDelivery(r.token(), part);
            deliveries.add(new Delivery(s.rack(), r.requester(), part, Delivery.DEFAULT_DELIVERY_PRIORITY));
            remaining -= part.count();
            if (remaining <= 0) {
                break;
            }
        }
        return deliveries;
    }

    /**
     * MC {@code getSuitabilityMetric}: {@code max(distance / 10, 1)} plus the warehouse's courier queue length. Deviation
     * from MC: an unknown requester (its hut removed) scores the worst; MC's requester keeps its saved location, so it
     * always gets a distance (or NPEs).
     */
    @Override
    public double suitability(RequestManager m, Request r) {
        Optional<BlockPos> from = RequesterLocation.of(colony, r.requester());
        if (from.isEmpty()) {
            return Double.MAX_VALUE;
        }
        int distance = (int) Math.sqrt((double) from.get().distSq(warehouse.position()));
        int queue = warehouse
                .module(WarehouseRequestQueue.class)
                .map(q -> q.tokens().size())
                .orElse(0);
        return Math.max(distance / 10, 1) + queue;
    }

    /**
     * An unknown requester counts as here: there is nowhere to deliver to. Deviation from MC: MC compares the
     * requester's saved location, which outlives its hut; ours is looked up among the colony's buildings.
     */
    private boolean isAtThisWarehouse(Request r) {
        return RequesterLocation.of(colony, r.requester())
                .map(warehouse.position()::equals)
                .orElse(true);
    }

    /** MC {@code getWarehouseInternalCount}: the matching items in all of {@code building}'s racks. */
    private int count(Building building, Deliverable d, RequestManager m) {
        int total = 0;
        for (RackStack s : matchingStacks(building, d, m)) {
            total += s.stack().count();
        }
        return total;
    }

    private List<RackStack> matchingStacks(Deliverable d, RequestManager m) {
        return matchingStacks(warehouse, d, m);
    }

    /**
     * MC {@code getMatchingItemStacksInWarehouse}: {@code building}'s matching slots, hut block first. A worn-out tool
     * never matches (the stack match of {@link Deliverable}): MC has destroyed it.
     */
    private List<RackStack> matchingStacks(Building building, Deliverable d, RequestManager m) {
        List<RackStack> out = new ArrayList<>();
        for (BlockPos rack : building.containers()) {
            for (ItemAmount stack : containers().stacks(rack)) {
                if (d.matches(stack, m.catalog())) {
                    out.add(new RackStack(rack, stack));
                }
            }
        }
        return out;
    }

    private ContainerAccess containers() {
        return colony.context().ports().containers();
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

    /** One slot of one rack. */
    private record RackStack(BlockPos rack, ItemAmount stack) {}
}
