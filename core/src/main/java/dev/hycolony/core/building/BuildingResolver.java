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
import dev.hycolony.core.request.model.RequesterId;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * MineColonies BuildingRequestResolver: a building serves its own requests from its hut's containers. The items
 * stay in the containers; they are recorded as deliveries (reserved) and the citizen comes to take them.
 */
public final class BuildingResolver implements Resolver {
    public static final int PRIORITY = 200;

    private final Building building;
    private final ContainerAccess containers;
    private final String id;
    private final RequesterId requesterId;

    public BuildingResolver(Building building, ContainerAccess containers) {
        this.building = building;
        this.containers = containers;
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
    public boolean handles(Deliverable requestable) {
        return true;
    }

    @Override
    public Optional<RequesterId> servesOnly() {
        return Optional.of(building.requesterId());
    }

    @Override
    public Optional<List<Deliverable>> attemptResolve(RequestManager m, Request r) {
        return Optional.of(List.of());
    }

    @Override
    public double suitability(RequestManager m, Request r) {
        return 0;
    }

    @Override
    public boolean canResolve(RequestManager m, Request r) {
        if (!r.requester().equals(building.requesterId()) || !r.requestable().canBeResolvedByBuilding()) {
            return false;
        }
        int total = 0;
        for (int n : available(m, r).values()) {
            total += n;
        }
        return total >= r.requestable().minCount();
    }

    @Override
    public void resolve(RequestManager m, Request r) {
        int left = r.requestable().count();
        for (Map.Entry<ItemKey, Integer> e : available(m, r).entrySet()) {
            if (left <= 0) {
                break;
            }
            int take = Math.min(left, e.getValue());
            m.addDelivery(r.token(), new ItemAmount(e.getKey(), take));
            left -= take;
        }
        m.updateState(r.token(), RequestState.RESOLVED);
    }

    /** Matching stock in the hut's containers, minus the deliveries of the building's other requests (until RECEIVED). */
    private Map<ItemKey, Integer> available(RequestManager m, Request r) {
        Deliverable d = r.requestable();
        Map<ItemKey, Integer> stock = new LinkedHashMap<>();
        containers.contents(building.containers()).forEach((item, n) -> {
            if (n > 0 && d.matches(item, m.catalog())) {
                stock.put(item, n);
            }
        });
        for (Request other : m.byRequester(building.requesterId())) {
            if (!other.equals(r)) {
                for (ItemAmount a : other.deliveries()) {
                    stock.computeIfPresent(a.item(), (k, n) -> n > a.count() ? n - a.count() : null);
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
