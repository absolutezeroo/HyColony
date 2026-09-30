package dev.hycolony.core.job.work;

import dev.hycolony.core.building.Building;
import dev.hycolony.core.citizen.CitizenData;
import dev.hycolony.core.colony.Colony;
import dev.hycolony.core.kernel.item.ItemAmount;
import dev.hycolony.core.request.Request;
import dev.hycolony.core.request.RequestManager;
import dev.hycolony.core.request.model.RequestState;
import dev.hycolony.core.request.model.StackRequest;
import java.util.List;

/**
 * A worker's own requests (MC AbstractEntityAIBasic checkIfNeedsItem, waitForRequests and lookForRequests): filed
 * under its hut with its citizen's id, they are sync, the worker waits for them in NEEDS_ITEM, then takes their items
 * from the hut through {@link WorkerStock}. Any job composes one around its citizen and hut. The hut's async requests
 * (filed without a citizen) are received by {@link #cleanAsync}.
 */
public final class SyncRequests {
    private final Colony colony;
    private final CitizenData citizen;
    private final Building hut;
    private final WorkerStock stock;

    public SyncRequests(Colony colony, CitizenData citizen, Building hut, WorkerStock stock) {
        this.colony = colony;
        this.citizen = citizen;
        this.hut = hut;
        this.stock = stock;
    }

    private RequestManager requests() {
        return colony.requests();
    }

    /** This worker's requests: those of the hut that carry its citizen's id, open or completed but not picked up. */
    public List<Request> mine() {
        return requests().byRequester(hut.requesterId()).stream()
                .filter(r -> r.citizenId() == citizen.id())
                .toList();
    }

    /** MC checkIfNeedsItem: a request of this worker is open, or completed and not yet picked up. */
    public boolean pending() {
        for (Request r : requests().byRequester(hut.requesterId())) {
            if (r.citizenId() == citizen.id()) {
                return true;
            }
        }
        return false;
    }

    /**
     * MC cleanAsync: the hut's completed requests filed without a citizen (the async ones, whose items wait in the
     * hut) are received, so their item may be asked for again. Returns false: MC's event never changes state.
     */
    public boolean cleanAsync() {
        for (Request r : requests().byRequester(hut.requesterId())) {
            if (r.citizenId() == Request.NO_CITIZEN && r.state() == RequestState.COMPLETED) {
                requests().updateState(r.token(), RequestState.RECEIVED);
            }
        }
        return false;
    }

    /**
     * MC lookForRequests, run at the hut: claims the open requests the hut can now serve ({@link #claimOpenFromHut}),
     * then picks up every completed one ({@link #pickUp}); true while some are still pending.
     */
    public boolean receiveAtHut() {
        claimOpenFromHut();
        for (Request r : mine()) {
            if (r.state() == RequestState.COMPLETED) {
                pickUp(r);
            }
        }
        return pending();
    }

    /**
     * MC checkForToolOrWeapon / lookForRequests, run while the worker waits: an open request of this worker that its
     * hut can now serve (a tool of the right type and level, or the full stack, beyond what other requests reserved)
     * goes to the hut's own resolver, which completes it with the hut's items for {@link #pickUp}. Covers what reached
     * the hut without a container event (hopper, restart, another player's window).
     */
    public void claimOpenFromHut() {
        requests()
                .onColonyUpdate(r -> r.requester().equals(hut.requesterId())
                        && r.citizenId() == citizen.id()
                        && r.state().isBefore(RequestState.COMPLETED)
                        && hut.stockCanServe(requests(), r));
    }

    /**
     * Takes a completed request's deliveries from the hut, then RECEIVED. Deliveries handed to the citizen (the
     * player's "Fournir") are already in the inventory: nothing is taken. A delivery the hut no longer holds is asked
     * again.
     */
    public void pickUp(Request r) {
        for (ItemAmount d : r.deliveredToCitizen() ? List.<ItemAmount>of() : r.deliveries()) {
            int there =
                    Math.min(d.count(), stock.hutCount(d.item())); // what does not fit stays in the hut, still there
            stock.take(d.item(), there);
            int missing = d.count() - there;
            if (missing > 0) {
                requests()
                        .createAndAssign(
                                hut,
                                r.requestable() instanceof StackRequest s
                                        ? new StackRequest(
                                                s.item(),
                                                missing,
                                                Math.min(s.minCount(), missing),
                                                s.canBeResolvedByBuilding())
                                        : r.requestable(),
                                citizen.id());
            }
        }
        requests().updateState(r.token(), RequestState.RECEIVED);
    }
}
