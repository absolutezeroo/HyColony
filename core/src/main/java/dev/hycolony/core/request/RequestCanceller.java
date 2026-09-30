package dev.hycolony.core.request;

import dev.hycolony.core.request.model.RequestState;
import dev.hycolony.core.request.model.RequestToken;
import dev.hycolony.core.request.model.RequesterId;
import java.util.ArrayList;

/**
 * MC onRequestCancelledDirectly: cancels a request after its whole subtree, telling its resolver and its requester,
 * then drops it from every index.
 */
final class RequestCanceller {
    private final RequestManager manager;
    private final RequestStore store;
    private final ResolverRegistry resolvers;

    RequestCanceller(RequestManager manager, RequestStore store, ResolverRegistry resolvers) {
        this.manager = manager;
        this.store = store;
        this.resolvers = resolvers;
    }

    void cancel(RequestToken token) {
        Request req = store.request(token);
        if (req == null) {
            return;
        }
        cancelChildren(req);

        Resolver resolver = store.resolverOf(token);
        if (resolver != null) {
            resolver.onCancelling(manager, req);
            store.unassign(token);
        }
        req.parent().map(store::request).ifPresent(p -> p.removeChild(token));
        req.setParent(null);
        store.changeState(req, RequestState.CANCELLED);
        if (resolver != null) {
            resolver.onCancelled(manager, req);
        }
        resolvers.requester(req).ifPresent(r -> r.onRequestCancelled(manager, req));
        store.clean(token);
    }

    /** Every request made by {@code requester}. */
    void cancelAllFrom(RequesterId requester) {
        store.tokensOf(requester).forEach(this::cancel);
    }

    /** The requests {@code requester} made for one citizen. */
    void cancelAllFrom(RequesterId requester, int citizenId) {
        for (RequestToken t : store.tokensOf(requester)) {
            Request r = store.request(t);
            if (r != null && r.citizenId() == citizenId) {
                cancel(t);
            }
        }
    }

    /** Every root request whose requester no longer exists; whether any was cancelled. */
    boolean cancelOrphans() {
        boolean cancelled = false;
        for (Request r : new ArrayList<>(store.all())) {
            if (store.contains(r.token()) && r.parent().isEmpty() && !resolvers.knowsRequester(r.requester())) {
                cancel(r.token());
                cancelled = true;
            }
        }
        return cancelled;
    }

    /** Cancels every child of {@code req} (a snapshot: each cancellation unlinks one). */
    void cancelChildren(Request req) {
        new ArrayList<>(req.children()).forEach(this::cancel);
    }
}
