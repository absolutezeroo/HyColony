package dev.hycolony.core.construction.builder;

import dev.hycolony.core.building.Building;
import dev.hycolony.core.citizen.CitizenData;
import dev.hycolony.core.colony.Colony;
import dev.hycolony.core.job.work.SyncRequests;
import dev.hycolony.core.job.work.WorkerStock;
import dev.hycolony.core.kernel.item.ItemKey;
import dev.hycolony.core.request.Request;
import dev.hycolony.core.request.RequestManager;
import dev.hycolony.core.request.model.RequestState;
import dev.hycolony.core.request.model.Requestable;
import dev.hycolony.core.request.model.StackRequest;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * The builder's requests (MC AbstractEntityAIBasic request helpers). Every request is filed under the builder hut.
 * Bucket requests are the building's (citizen -1, async: they never block); the request for the item needed right now
 * carries the citizen's id (sync: the builder waits for it in NEEDS_ITEM), and is picked up from the hut through
 * {@link SyncRequests}, as every worker's; tools are asked by {@link dev.hycolony.core.job.work.ToolRequests}.
 */
final class BuilderRequests {
    private final Colony colony;
    private final CitizenData citizen;
    private final Building hut;
    private final SyncRequests sync;

    BuilderRequests(Colony colony, CitizenData citizen, Building hut, WorkerStock stock) {
        this.colony = colony;
        this.citizen = citizen;
        this.hut = hut;
        this.sync = new SyncRequests(colony, citizen, hut, stock);
    }

    private RequestManager requests() {
        return colony.requests();
    }

    /** This builder's own (sync) requests: those of the hut that carry its citizen's id. */
    List<Request> mine() {
        return sync.mine();
    }

    /** Any live request of this builder, open or completed but not yet picked up. */
    boolean hasSyncRequests() {
        return sync.pending();
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

    /** See {@link SyncRequests#claimOpenFromHut}. */
    void claimOpenFromHut() {
        sync.claimOpenFromHut();
    }

    /** See {@link SyncRequests#pickUp}. */
    void pickUp(Request r) {
        sync.pickUp(r);
    }
}
