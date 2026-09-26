package dev.hycolony.core.request;

import dev.hycolony.core.kernel.item.ItemAmount;
import dev.hycolony.core.kernel.port.ItemCatalog;
import dev.hycolony.core.request.model.Deliverable;
import dev.hycolony.core.request.model.RequestState;
import dev.hycolony.core.request.model.RequestToken;
import dev.hycolony.core.request.model.RequesterId;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.function.Consumer;
import java.util.function.Predicate;

/**
 * One per colony. Port of MineColonies' StandardRequestManager + RequestHandler/ResolverHandler/ProviderHandler,
 * with the data stores collapsed into direct maps (plus the request -> resolver inverse index): {@link RequestStore}
 * holds the requests, {@link ResolverRegistry} the resolvers, {@link RequestAssigner} assigns,
 * {@link RequestTransitions} applies state changes and {@link RequestCanceller} cancels.
 *
 * <p>Every public mutation is queued and processed FIFO by a non-reentrant loop ({@link OperationQueue}): a callback
 * (requester or resolver) that calls back into the manager only enqueues, and its work runs after the current step.
 * Internal cascades (child completion, cancellation of a subtree, ...) keep MineColonies' synchronous callback order.
 */
public final class RequestManager {
    /** The colony ticks the manager every 11 game ticks (MineColonies' request system rate). */
    public static final int TICK_INTERVAL = 11;

    private final ItemCatalog catalog;
    private final ResolverRegistry resolvers;
    private final RequestStore store = new RequestStore();
    private final OperationQueue queue = new OperationQueue();
    private final RequestCanceller canceller;
    private final RequestAssigner assigner;
    private final RequestTransitions transitions;

    public RequestManager(RequesterRegistry requesters, ItemCatalog catalog) {
        this.resolvers = new ResolverRegistry(requesters);
        this.catalog = catalog;
        this.canceller = new RequestCanceller(this, store, resolvers);
        this.assigner = new RequestAssigner(this, store, resolvers, canceller);
        this.transitions = new RequestTransitions(this, store, resolvers, assigner, canceller);
    }

    /** Player, retrying: never removed. */
    public void registerBuiltIn(Resolver r) {
        resolvers.register(r);
    }

    public void onProviderAdded(ResolverProvider p) {
        resolvers.addProvider(p);
    }

    /** Reassigns its requests with all its resolvers blacklisted, then drops them (ProviderHandler.removeProvider). */
    public void onProviderRemoved(ResolverProvider p) {
        queue.submit(() -> resolvers.provider(p.providerId()).ifPresent(list -> {
            assigner.reassignAway(list);
            resolvers.removeProvider(p.providerId(), list);
        }));
    }

    public RequestToken createAndAssign(Requester requester, Deliverable what, int citizenId) {
        Request req = store.create(requester.requesterId(), what, citizenId);
        queue.submit(() -> assigner.assignUnassigned(List.of(req.token()), Set.of()));
        return req.token();
    }

    /** A child created by a resolver for one of its requests; inherits the parent's blacklist. */
    public RequestToken createChild(Resolver parentResolver, RequestToken parent, Deliverable what) {
        Request p = store.require(parent);
        Request child = store.create(parentResolver.requesterId(), what, -1);
        child.setParent(parent);
        p.addChild(child.token());
        queue.submit(() -> assigner.assignUnassigned(List.of(child.token()), p.blacklist()));
        return child.token();
    }

    public void assign(RequestToken token, Set<String> blacklist) {
        store.require(token);
        Set<String> bl = Set.copyOf(blacklist);
        queue.submit(() -> {
            Request req = store.request(token);
            if (req != null) {
                if (store.isAssigned(token)) {
                    throw new IllegalArgumentException("Request already assigned: " + token);
                }
                assigner.assign(req, bl);
            }
        });
    }

    public void reassign(RequestToken token, Set<String> blacklist) {
        store.require(token);
        Set<String> bl = Set.copyOf(blacklist);
        queue.submit(() -> {
            Request req = store.request(token);
            if (req != null) {
                assigner.reassign(req, bl);
            }
        });
    }

    /** Public transitions only: RESOLVED, COMPLETED, CANCELLED, FAILED, RECEIVED (use {@link #overrule}). */
    public void updateState(RequestToken token, RequestState newState) {
        if (!RequestTransitions.isPublic(newState)) {
            throw new IllegalArgumentException("Not a public transition: " + newState);
        }
        store.require(token);
        queue.submit(() -> {
            Request req = store.request(token);
            if (req != null) {
                transitions.transition(req, newState);
            }
        });
    }

    /**
     * The player provided the items: cancel children, then COMPLETED. MC StandardRequestManager.overruleRequest.
     * Deviation from MC: applied once; MineColonies ran it twice.
     */
    public void overrule(RequestToken token, List<ItemAmount> delivered) {
        overrule(token, delivered, false);
    }

    /**
     * {@code toCitizen}: the items were handed to the requesting citizen ("Fournir"), not left in the hut; the
     * request remembers it ({@link Request#deliveredToCitizen()}) so its pick-up takes nothing from the hut.
     */
    public void overrule(RequestToken token, List<ItemAmount> delivered, boolean toCitizen) {
        store.require(token);
        List<ItemAmount> items = List.copyOf(delivered);
        queue.submit(() -> {
            Request req = store.request(token);
            if (req != null && req.state().ordinal() < RequestState.COMPLETED.ordinal()) {
                req.setDeliveredToCitizen(toCitizen);
                transitions.overrule(req, items);
            }
        });
    }

    /** Cancels every request made by {@code requester} (subtrees first, requester notified), e.g. a removed building. */
    public void cancelAllFrom(RequesterId requester) {
        queue.submit(() -> canceller.cancelAllFrom(requester));
    }

    /** Cancels the requests {@code requester} made for one citizen (a worker leaving its building). */
    public void cancelAllFrom(RequesterId requester, int citizenId) {
        queue.submit(() -> canceller.cancelAllFrom(requester, citizenId));
    }

    /**
     * Cancels every root request whose requester no longer exists (e.g. a building missing from a save); each one
     * logs the missing requester. Returns whether any was cancelled (always false if called re-entrantly).
     */
    public boolean cancelOrphans() {
        boolean[] cancelled = {false};
        queue.submit(() -> cancelled[0] = canceller.cancelOrphans());
        return cancelled[0];
    }

    /** MC moveToSyncCitizen: a building's (async) request becomes the citizen's, who now waits for it. */
    public void makeSync(RequestToken token, int citizenId) {
        store.require(token);
        queue.submit(() -> {
            Request req = store.request(token);
            if (req != null) {
                req.setCitizenId(citizenId);
            }
        });
    }

    /** Sees every request as it is created, even one closed within the same tick (simulations, debugging). */
    public void setCreationListener(Consumer<Request> listener) {
        store.setCreationListener(listener);
    }

    public void onColonyUpdate(Predicate<Request> which) {
        queue.submit(() -> resolvers.snapshot().forEach(r -> r.onColonyUpdate(this, which)));
    }

    /** Ticks every registered resolver. */
    public void tick() {
        queue.submit(() -> resolvers.snapshot().forEach(r -> r.tick(this)));
    }

    public void addDelivery(RequestToken token, ItemAmount amount) {
        store.require(token).addDelivery(amount);
    }

    public Optional<Request> get(RequestToken token) {
        return Optional.ofNullable(store.request(token));
    }

    /** O(1): inverse index. */
    public Optional<Resolver> resolverOf(RequestToken token) {
        return Optional.ofNullable(store.resolverOf(token));
    }

    public List<Request> byRequester(RequesterId id) {
        return store.byRequester(id);
    }

    public List<Request> assignedTo(String resolverId) {
        return store.assignedTo(resolverId);
    }

    public Collection<Request> all() {
        return store.all();
    }

    public ItemCatalog catalog() {
        return catalog;
    }

    public Optional<Resolver> resolver(String resolverId) {
        return resolvers.byId(resolverId);
    }

    RequestStore store() {
        return store;
    }

    /**
     * Reassigns a loaded request whose resolver no longer exists, after cancelling its children: MC
     * ResolverHandler.removeResolverWithAssignedRequests. Deviation from MC: MC runs it when a provider is removed;
     * here the resolver vanished between save and load, and the request is kept rather than dropped.
     */
    void reassignLoaded(RequestToken token) {
        queue.submit(() -> {
            Request req = store.request(token);
            if (req != null) {
                canceller.cancelChildren(req);
                assigner.reassign(req, req.blacklist());
            }
        });
    }
}
