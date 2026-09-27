package dev.hycolony.core.construction.builder;

import dev.hycolony.core.building.Building;
import dev.hycolony.core.citizen.CitizenData;
import dev.hycolony.core.colony.Colony;
import dev.hycolony.core.kernel.item.ItemAmount;
import dev.hycolony.core.kernel.item.ItemKey;
import dev.hycolony.core.kernel.item.ToolType;
import dev.hycolony.core.request.Request;
import dev.hycolony.core.request.RequestManager;
import dev.hycolony.core.request.model.RequestState;
import dev.hycolony.core.request.model.Requestable;
import dev.hycolony.core.request.model.StackRequest;
import dev.hycolony.core.request.model.ToolRequest;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * The builder's requests (MC AbstractEntityAIBasic request helpers). Every request is filed under the builder hut.
 * Bucket requests are the building's (citizen -1, async: they never block); the request for the item needed right now
 * carries the citizen's id (sync: the builder waits for it in NEEDS_ITEM). Deliveries are picked up from the hut
 * through {@link BuilderStock}.
 */
final class BuilderRequests {
    private final Colony colony;
    private final CitizenData citizen;
    private final Building hut;
    private final BuilderStock stock;

    BuilderRequests(Colony colony, CitizenData citizen, Building hut, BuilderStock stock) {
        this.colony = colony;
        this.citizen = citizen;
        this.hut = hut;
        this.stock = stock;
    }

    private RequestManager requests() {
        return colony.requests();
    }

    /** This builder's own (sync) requests: those of the hut that carry its citizen's id. */
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

    private void request(Requestable what) {
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
                if (r.citizenId() == -1 && r.state().isBefore(RequestState.COMPLETED)) {
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
                        && r.state().isBefore(RequestState.COMPLETED)
                        && hut.resolvers().stream().anyMatch(res -> res.canResolve(requests(), r)));
    }

    /**
     * One ToolRequest(type, 0, hut max equipment level) unless one of that type is live (MC checkForToolOrWeapon:
     * {@code Tool(type, TOOL_LEVEL_WOOD_OR_GOLD, max(maxEquip, min))}; min is 0, so the max is maxEquip).
     */
    void requestTool(ToolType type) {
        for (Request r : requests().byRequester(hut.requesterId())) {
            if (r.requestable() instanceof ToolRequest t && t.type() == type) {
                return;
            }
        }
        request(new ToolRequest(type, 0, hut.maxEquipmentLevel()));
    }

    /**
     * Takes a completed request's deliveries from the hut, then RECEIVED. Deliveries handed to the citizen (the
     * player's "Fournir") are already in the inventory: nothing is taken. A delivery the hut no longer holds is asked
     * again.
     */
    void pickUp(Request r) {
        for (ItemAmount d : r.deliveredToCitizen() ? List.<ItemAmount>of() : r.deliveries()) {
            int there =
                    Math.min(d.count(), stock.hutCount(d.item())); // what does not fit stays in the hut, still there
            stock.take(d.item(), there);
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
