package dev.hycolony.core.construction;

import dev.hycolony.core.building.Building;
import dev.hycolony.core.citizen.CitizenData;
import dev.hycolony.core.colony.Colony;
import dev.hycolony.core.kernel.BlockPos;
import dev.hycolony.core.kernel.item.Inventory;
import dev.hycolony.core.kernel.item.ItemAmount;
import dev.hycolony.core.kernel.item.ItemKey;
import dev.hycolony.core.kernel.item.ToolInfo;
import dev.hycolony.core.kernel.item.ToolType;
import dev.hycolony.core.kernel.port.ContainerAccess;
import dev.hycolony.core.kernel.port.ItemCatalog;
import dev.hycolony.core.request.Deliverable;
import dev.hycolony.core.request.Request;
import dev.hycolony.core.request.RequestManager;
import dev.hycolony.core.request.RequestState;
import dev.hycolony.core.request.StackRequest;
import dev.hycolony.core.request.ToolRequest;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.ToIntFunction;

/**
 * The builder's items: its inventory, its hut's containers and its requests (MC AbstractEntityAIBasic dump, pickup
 * and tool helpers). Every request is filed under the builder hut. Bucket requests are the building's (citizen -1,
 * async: they never block); the request for the item needed right now carries the citizen's id (sync: the builder
 * waits for it in NEEDS_ITEM).
 */
final class BuilderStock {
    private static final System.Logger LOG = System.getLogger(BuilderStock.class.getName());

    private final Colony colony;
    private final CitizenData citizen;
    private final Building hut;
    private final ItemCatalog catalog;
    private final ContainerAccess containers;
    private final ToIntFunction<ItemKey> maxStack;

    BuilderStock(Colony colony, CitizenData citizen, Building hut) {
        this.colony = colony;
        this.citizen = citizen;
        this.hut = hut;
        this.catalog = colony.context().ports().catalog();
        this.containers = colony.context().ports().containers();
        this.maxStack = catalog::maxStack;
    }

    Inventory inventory() {
        return citizen.inventory();
    }

    int hutCount(ItemKey item) {
        return containers.count(hut.containers(), item);
    }

    /**
     * Moves up to {@code max} from the hut to the inventory; what does not fit goes back to the hut. Returns how many
     * landed in the inventory.
     */
    int take(ItemKey item, int max) {
        if (max <= 0) {
            return 0;
        }
        List<BlockPos> hc = hut.containers();
        int got = containers.extract(hc, item, max);
        if (got <= 0) {
            return 0;
        }
        ItemAmount rest = inventory().insert(new ItemAmount(item, got), maxStack);
        if (rest == null) {
            return got;
        }
        lose(containers.insert(hc, rest));
        return got - rest.count();
    }

    /** Tops the inventory up to each amount of {@code bucket} from the hut. */
    void takeBucket(Map<ItemKey, Integer> bucket) {
        bucket.forEach((item, n) -> take(item, n - inventory().count(item)));
    }

    /**
     * Stores everything in the hut but (MC keepX) the {@code keep} amounts and one tool per type. False when the hut
     * could not take it all: what could be stored is stored, the rest stays.
     */
    boolean dump(Map<ItemKey, Integer> keep) {
        List<BlockPos> hc = hut.containers();
        Map<ItemKey, Integer> keepLeft = new HashMap<>(keep);
        Set<ToolType> toolKept = EnumSet.noneOf(ToolType.class);
        for (ItemAmount a : inventory().contents()) {
            ToolInfo tool = catalog.tool(a.item()).orElse(null);
            if (tool != null && toolKept.add(tool.type())) {
                continue;
            }
            int kept = Math.min(a.count(), keepLeft.getOrDefault(a.item(), 0));
            keepLeft.computeIfPresent(a.item(), (k, n) -> n - kept);
            if (kept == a.count()) {
                continue;
            }
            ItemAmount rest = containers.insert(hc, a.withCount(a.count() - kept));
            int stored = a.count() - kept - (rest == null ? 0 : rest.count());
            if (stored > 0) {
                inventory().extract(a.item(), stored);
            }
            if (rest != null) {
                return false;
            }
        }
        return true;
    }

    /** Drops go to the inventory, then the hut; what fits in neither is lost (logged). */
    void storeDrops(List<ItemAmount> drops) {
        for (ItemAmount d : drops) {
            ItemAmount rest = inventory().insert(d, maxStack);
            if (rest != null) {
                lose(containers.insert(hut.containers(), rest));
            }
        }
    }

    /** Items that fit neither in the inventory nor in the hut: logged as {@code debrisLost}. */
    private void lose(ItemAmount rest) {
        if (rest != null) {
            colony.log().add("debrisLost", colony.day(), rest.item().id(), String.valueOf(rest.count()));
            LOG.log(
                    System.Logger.Level.DEBUG,
                    "Builder {0}: {1} x {2} lost, inventory and hut full",
                    citizen.name(),
                    rest.count(),
                    rest.item().id());
        }
    }

    /**
     * MC getMostEfficientTool: the lowest-level tool of {@code type} in the inventory within the hut's level (the
     * least powerful one that does the job). Null if none.
     */
    ItemKey toolInInventory(ToolType type) {
        ItemKey best = null;
        int bestLevel = Integer.MAX_VALUE;
        for (ItemAmount a : inventory().contents()) {
            ToolInfo info = catalog.tool(a.item()).orElse(null);
            if (info != null && info.type() == type && info.level() <= hut.level() && info.level() < bestLevel) {
                best = a.item();
                bestLevel = info.level();
            }
        }
        return best;
    }

    /** A tool of {@code type} within the hut's level stored in the hut, or null. */
    ItemKey toolInHut(ToolType type) {
        for (ItemKey item : containers.contents(hut.containers()).keySet()) {
            ToolInfo info = catalog.tool(item).orElse(null);
            if (info != null && info.type() == type && info.level() <= hut.level()) {
                return item;
            }
        }
        return null;
    }

    float toolSpeed(ItemKey tool) {
        return tool == null ? 1f : catalog.tool(tool).map(ToolInfo::speed).orElse(1f);
    }

    // ---- requests ----

    private RequestManager requests() {
        return colony.requests();
    }

    List<Request> mine() {
        return requests().byRequester(hut.requesterId()).stream()
                .filter(r -> r.citizenId() == citizen.id())
                .toList();
    }

    /** Any live request of this builder, open or completed but not yet picked up. */
    boolean hasSyncRequests() {
        for (Request r : requests().byRequester(hut.requesterId())) {
            if (r.citizenId() == citizen.id()) {
                return true;
            }
        }
        return false;
    }

    /** Items with a live stack request of the hut. */
    Set<ItemKey> requestedItems() {
        Set<ItemKey> out = new HashSet<>();
        for (Request r : requests().byRequester(hut.requesterId())) {
            if (r.requestable() instanceof StackRequest s) {
                out.add(s.item());
            }
        }
        return out;
    }

    private void request(Deliverable what) {
        requests().createAndAssign(hut, what, citizen.id());
    }

    /** MC checkOrRequestBucket: a building-level (async) request, min 1. */
    void requestForBucket(ItemKey item, int count) {
        requests().createAndAssign(hut, new StackRequest(item, count, 1, true), -1);
    }

    /**
     * MC hasListOfResInInvOrRequest: the item this placement needs becomes a sync request. A live building request
     * for it is moved to the citizen (moveToSyncCitizen) rather than duplicated; an own one is left alone.
     */
    void requestNow(ItemKey item, int count) {
        for (Request r : requests().byRequester(hut.requesterId())) {
            if (r.requestable() instanceof StackRequest s && s.item().equals(item)) {
                if (r.citizenId() == -1 && r.state().ordinal() < RequestState.COMPLETED.ordinal()) {
                    requests().makeSync(r.token(), citizen.id());
                    return;
                }
                if (r.citizenId() == citizen.id()) {
                    return;
                }
            }
        }
        request(new StackRequest(item, count, 1, true));
    }

    /**
     * MC cleanAsync / markRequestAsAccepted: a completed building request left its items in the hut, where the
     * builder takes them like any stock. RECEIVED, so the item can be asked for again.
     */
    void receiveCompletedBuildingRequests() {
        for (Request r : requests().byRequester(hut.requesterId())) {
            if (r.citizenId() == -1 && r.state() == RequestState.COMPLETED) {
                requests().updateState(r.token(), RequestState.RECEIVED);
            }
        }
    }

    /**
     * MC checkForToolOrWeapon / lookForRequests, run while the builder waits: an open request of this builder that its
     * hut can now serve (a tool of the right type and level, or the full stack, beyond what other requests reserved)
     * goes to the hut's own resolver, which completes it with the hut's items for {@link #pickUp}. Covers what reached
     * the hut without a container event (hopper, restart, another player's window).
     */
    void claimOpenFromHut() {
        requests()
                .onColonyUpdate(r -> r.requester().equals(hut.requesterId())
                        && r.citizenId() == citizen.id()
                        && r.state().ordinal() < RequestState.COMPLETED.ordinal()
                        && hut.resolvers().stream().anyMatch(res -> res.canResolve(requests(), r)));
    }

    /** One ToolRequest(type, 0, hut level) unless one of that type is live. */
    void requestTool(ToolType type) {
        for (Request r : requests().byRequester(hut.requesterId())) {
            if (r.requestable() instanceof ToolRequest t && t.type() == type) {
                return;
            }
        }
        request(new ToolRequest(type, 0, hut.level()));
    }

    /**
     * Takes a completed request's deliveries from the hut, then RECEIVED. Deliveries handed to the citizen (the
     * player's "Fournir") are already in the inventory: nothing is taken. A delivery the hut no longer holds is asked
     * again.
     */
    void pickUp(Request r) {
        for (ItemAmount d : r.deliveredToCitizen() ? List.<ItemAmount>of() : r.deliveries()) {
            int there = Math.min(d.count(), hutCount(d.item())); // what does not fit stays in the hut, still there
            take(d.item(), there);
            int missing = d.count() - there;
            if (missing > 0) {
                request(
                        r.requestable() instanceof StackRequest s
                                ? new StackRequest(
                                        s.item(), missing, Math.min(s.minCount(), missing), s.canBeResolvedByBuilding())
                                : r.requestable());
            }
        }
        requests().updateState(r.token(), RequestState.RECEIVED);
    }
}
