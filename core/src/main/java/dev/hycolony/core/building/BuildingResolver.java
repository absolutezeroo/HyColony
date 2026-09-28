package dev.hycolony.core.building;

import dev.hycolony.core.kernel.BlockPos;
import dev.hycolony.core.kernel.item.ItemAmount;
import dev.hycolony.core.kernel.item.ItemKey;
import dev.hycolony.core.kernel.port.ContainerAccess;
import dev.hycolony.core.request.Request;
import dev.hycolony.core.request.RequestManager;
import dev.hycolony.core.request.Resolver;
import dev.hycolony.core.request.model.Deliverable;
import dev.hycolony.core.request.model.RequestState;
import dev.hycolony.core.request.model.Requestable;
import dev.hycolony.core.request.model.RequesterId;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.ToIntBiFunction;

/**
 * MineColonies BuildingRequestResolver: a building serves its own requests from its hut's containers. The items
 * stay in the containers; they are recorded as deliveries (reserved) and the citizen comes to take them.
 */
public final class BuildingResolver implements Resolver {
    public static final int PRIORITY = 200;

    private final Building building;
    private final ContainerAccess containers;
    /** MC reservedStacksExcluding: how many of an item the building holds back from a request (crafting tasks). */
    private final ToIntBiFunction<Request, ItemKey> reserved;

    private final String id;
    private final RequesterId requesterId;

    public BuildingResolver(Building building, ContainerAccess containers, ToIntBiFunction<Request, ItemKey> reserved) {
        this.building = building;
        this.containers = containers;
        this.reserved = reserved;
        this.id = building.requesterId().value();
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
     * MC BuildingRequestResolver serves the requests made at its building's location: the building's own, and those
     * its other resolvers make (a crafting task's ingredients).
     */
    @Override
    public Set<RequesterId> servesOnly() {
        Set<RequesterId> served = new HashSet<>();
        served.add(building.requesterId());
        building.resolvers().forEach(r -> served.add(r.requesterId()));
        return served;
    }

    @Override
    public Optional<List<Requestable>> attemptResolve(RequestManager m, Request r) {
        return Optional.of(List.of());
    }

    @Override
    public double suitability(RequestManager m, Request r) {
        return 0;
    }

    @Override
    public boolean canResolve(RequestManager m, Request r) {
        Deliverable d = r.deliverable().orElse(null);
        if (d == null || !servesOnly().contains(r.requester()) || !d.canBeResolvedByBuilding()) {
            return false;
        }
        int total = 0;
        for (int n : available(m, r, d).values()) {
            total += n;
        }
        return total >= d.minCount();
    }

    @Override
    public void resolve(RequestManager m, Request r) {
        Deliverable d = r.deliverable().orElseThrow(); // canResolve took only deliverables
        int left = d.count();
        for (Map.Entry<ItemKey, Integer> e : available(m, r, d).entrySet()) {
            if (left <= 0) {
                break;
            }
            int take = Math.min(left, e.getValue());
            m.addDelivery(r.token(), new ItemAmount(e.getKey(), take));
            left -= take;
        }
        m.updateState(r.token(), RequestState.RESOLVED);
    }

    /**
     * Matching stock in the hut's containers (never a worn-out tool), minus the deliveries of the building's other
     * requests (until RECEIVED).
     */
    private Map<ItemKey, Integer> available(RequestManager m, Request r, Deliverable d) {
        Map<ItemKey, Integer> stock = matching(m, d);
        stock.replaceAll((item, n) -> n - reserved.applyAsInt(r, item));
        stock.values().removeIf(n -> n <= 0);
        for (RequesterId requester : servesOnly()) {
            for (Request other : m.byRequester(requester)) {
                if (!other.equals(r)) {
                    withoutDeliveries(stock, other);
                }
            }
        }
        return stock;
    }

    /** Takes {@code other}'s deliveries off {@code stock}: those items are the other request's. */
    private static void withoutDeliveries(Map<ItemKey, Integer> stock, Request other) {
        for (ItemAmount a : other.deliveries()) {
            stock.computeIfPresent(a.item(), (_, n) -> n > a.count() ? n - a.count() : null);
        }
    }

    /** The hut's stacks that match {@code d}, by item (a worn-out tool never does). */
    private Map<ItemKey, Integer> matching(RequestManager m, Deliverable d) {
        Map<ItemKey, Integer> stock = new LinkedHashMap<>();
        for (BlockPos container : building.containers()) {
            for (ItemAmount a : containers.stacks(container)) {
                if (d.matches(a, m.catalog())) {
                    stock.merge(a.item(), a.count(), Integer::sum);
                }
            }
        }
        return stock;
    }

    @Override
    public RequesterId requesterId() {
        return requesterId;
    }

    @Override
    public BlockPos location() {
        return building.position();
    }

    @Override
    public String displayName() {
        return building.displayName();
    }

    @Override
    public void onRequestComplete(RequestManager manager, Request request) {}

    @Override
    public void onRequestCancelled(RequestManager manager, Request request) {}
}
