package dev.hycolony.core.construction.builder;

import dev.hycolony.core.building.Building;
import dev.hycolony.core.citizen.CitizenData;
import dev.hycolony.core.colony.Colony;
import dev.hycolony.core.kernel.item.ItemKey;
import dev.hycolony.core.request.Request;
import dev.hycolony.core.request.RequestManager;
import dev.hycolony.core.request.model.RequestState;
import dev.hycolony.core.request.model.Requestable;
import dev.hycolony.core.request.model.StackRequest;
import java.util.HashSet;
import java.util.Set;

/**
 * The builder's requests (MC AbstractEntityAIBasic request helpers). Every request is filed under the builder hut.
 * Bucket requests are the building's (citizen -1, async: they never block); the request for the item needed right now
 * carries the citizen's id (sync: the builder waits for it in NEEDS_ITEM), and is picked up from the hut through
 * the context's {@link dev.hycolony.core.job.work.SyncRequests}, as every worker's; tools are asked by
 * {@link dev.hycolony.core.job.work.ToolRequests}.
 */
final class BuilderRequests {
    private final Colony colony;
    private final CitizenData citizen;
    private final Building hut;

    BuilderRequests(Colony colony, CitizenData citizen, Building hut) {
        this.colony = colony;
        this.citizen = citizen;
        this.hut = hut;
    }

    private RequestManager requests() {
        return colony.requests();
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

    /**
     * Cancels the hut's stack requests, not yet completed, for an item outside {@code needed}: a save may hold some
     * for an item no block costs any more, which nothing would ever answer.
     *
     * <p>Deviation from MC: MC never revises its builder's requests when it loads an order. A save written before the
     * builder asked for the item that places a block (docs/research/audit-monde-hytale.md A-15) may hold requests for
     * a container item no block costs any more; they are repaired on every load. Otherwise this only cancels, after a
     * restart, a bucket request whose cells were all placed meanwhile, which MC would still deliver; a finished order
     * cancels all its requests anyway ({@code BuilderAI.completeBuild}).
     */
    void cancelUnneeded(Set<ItemKey> needed) {
        for (Request r : requests().byRequester(hut.requesterId())) {
            if (r.requestable() instanceof StackRequest s
                    && !needed.contains(s.item())
                    && r.state().isBefore(RequestState.COMPLETED)) {
                requests().updateState(r.token(), RequestState.CANCELLED);
            }
        }
    }

    private void request(Requestable what) {
        requests().createAndAssign(hut, what, citizen.id());
    }

    /** MC checkOrRequestBucket: a building-level (async) request, min 1. */
    void requestForBucket(ItemKey item, int count) {
        requests().createAndAssign(hut, new StackRequest(item, count, 1, true), Request.NO_CITIZEN);
    }

    /**
     * MC hasListOfResInInvOrRequest: the item this placement needs becomes a sync request. A live building request
     * for it is moved to the citizen (moveToSyncCitizen) rather than duplicated; an own one is left alone.
     */
    void requestNow(ItemKey item, int count) {
        for (Request r : requests().byRequester(hut.requesterId())) {
            if (r.requestable() instanceof StackRequest s && s.item().equals(item)) {
                if (r.citizenId() == Request.NO_CITIZEN && r.state().isBefore(RequestState.COMPLETED)) {
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
}
