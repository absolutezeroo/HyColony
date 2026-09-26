package dev.hycolony.core.request;

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
        req.setState(RequestState.CANCELLED);
        if (resolver != null) {
            resolver.onCancelled(manager, req);
        }
        resolvers.requester(req).ifPresent(r -> r.onRequestCancelled(manager, req));
        store.clean(token);
    }

    /** Cancels every child of {@code req} (a snapshot: each cancellation unlinks one). */
    void cancelChildren(Request req) {
        new ArrayList<>(req.children()).forEach(this::cancel);
    }
}
