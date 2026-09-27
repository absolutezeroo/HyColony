package dev.hycolony.core.request;

import dev.hycolony.core.kernel.item.ItemAmount;
import dev.hycolony.core.request.model.RequestState;
import dev.hycolony.core.request.model.RequestToken;
import dev.hycolony.core.request.model.Requestable;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;

/**
 * MC StandardRequestManager.updateRequestState: applies a request's new state and its cascade (follow-ups, the
 * parent resolved or completed once its last child is, a cancelled child reassigning its parent).
 */
final class RequestTransitions {
    /** The states a caller outside the request system may set; the others are the manager's own. */
    private static final Set<RequestState> PUBLIC_STATES = EnumSet.of(
            RequestState.RESOLVED,
            RequestState.COMPLETED,
            RequestState.CANCELLED,
            RequestState.FAILED,
            RequestState.RECEIVED);

    private final RequestManager manager;
    private final RequestStore store;
    private final ResolverRegistry resolvers;
    private final RequestAssigner assigner;
    private final RequestCanceller canceller;

    RequestTransitions(
            RequestManager manager,
            RequestStore store,
            ResolverRegistry resolvers,
            RequestAssigner assigner,
            RequestCanceller canceller) {
        this.manager = manager;
        this.store = store;
        this.resolvers = resolvers;
        this.assigner = assigner;
        this.canceller = canceller;
    }

    static boolean isPublic(RequestState state) {
        return PUBLIC_STATES.contains(state);
    }

    void transition(Request req, RequestState state) {
        req.setState(state);
        switch (state) {
            case RESOLVED -> onResolved(req);
            case COMPLETED -> onCompleted(req);
            case CANCELLED, FAILED -> onCancelled(req);
            case RECEIVED -> store.clean(req.token());
            default -> {}
        }
    }

    private void onResolved(Request req) {
        Resolver resolver = store.resolverOf(req.token());
        List<Requestable> followups = resolver == null ? List.of() : resolver.followups(manager, req);
        req.setState(RequestState.FOLLOWUP_IN_PROGRESS);
        if (resolver != null && !followups.isEmpty()) {
            List<RequestToken> tokens = assigner.createAll(resolver, followups);
            for (RequestToken c : tokens) {
                store.require(c).setParent(req.token());
                req.addChild(c);
            }
            assigner.assignUnassigned(tokens, Set.of());
        }
        if (req.children().isEmpty()) {
            transition(req, RequestState.COMPLETED);
        }
    }

    private void onCompleted(Request req) {
        resolvers.requester(req).ifPresent(r -> r.onRequestComplete(manager, req));
        Request parent = req.parent().map(store::request).orElse(null);
        if (parent == null) {
            return;
        }
        transition(req, RequestState.RECEIVED);
        parent.removeChild(req.token());
        req.setParent(null);
        if (parent.children().isEmpty()) {
            if (parent.state() == RequestState.IN_PROGRESS) {
                assigner.resolve(parent);
            } else if (parent.state() == RequestState.FOLLOWUP_IN_PROGRESS) {
                transition(parent, RequestState.COMPLETED);
            }
        }
    }

    /** RequestHandler.onRequestOverruled, preceded by overrideCurrentDeliveries. */
    void overrule(Request req, List<ItemAmount> delivered) {
        req.setState(RequestState.OVERRULED);
        Resolver resolver = store.resolverOf(req.token());
        if (resolver == null) {
            store.clean(req.token());
            return;
        }
        canceller.cancelChildren(req);
        resolver.onCancelling(manager, req);
        if (!delivered.isEmpty()) {
            req.setDeliveries(delivered);
        }
        transition(req, RequestState.COMPLETED);
        resolver.onCancelled(manager, req);
    }

    private void onCancelled(Request req) {
        Request parent = req.parent().map(store::request).orElse(null);
        if (parent == null) {
            canceller.cancel(req.token());
            return;
        }
        // onChildRequestCancelled; the parent keeps the blacklist it was assigned with.
        parent.setDeliveries(List.of());
        canceller.cancelChildren(parent);
        assigner.reassign(parent, parent.blacklist());
    }
}
