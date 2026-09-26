package dev.hycolony.core.request;

import dev.hycolony.core.request.model.Deliverable;
import dev.hycolony.core.request.model.RequestState;
import dev.hycolony.core.request.model.RequestToken;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * MC RequestHandler.assignRequestDefault: offers a request to its candidate resolvers, highest priority first, keeps
 * the most suitable one of the first priority that takes it, then resolves the request with it.
 */
final class RequestAssigner {
    private static final System.Logger LOG = System.getLogger(RequestManager.class.getName());

    /** A resolver that took the request, with its suitability and the children it asked for. */
    private record Attempt(Resolver resolver, double metric, List<RequestToken> children) {}

    private final RequestManager manager;
    private final RequestStore store;
    private final ResolverRegistry resolvers;
    private final RequestCanceller canceller;

    RequestAssigner(
            RequestManager manager, RequestStore store, ResolverRegistry resolvers, RequestCanceller canceller) {
        this.manager = manager;
        this.store = store;
        this.resolvers = resolvers;
        this.canceller = canceller;
    }

    void assign(Request req, Set<String> blacklist) {
        req.setBlacklist(blacklist);
        req.setState(RequestState.ASSIGNING);
        Attempt winner = null;
        for (Resolver r : resolvers.candidates(req.requester())) {
            if (!offeredTo(r, req, blacklist)) {
                continue;
            }
            if (winner != null && winner.resolver().priority() != r.priority()) {
                break;
            }
            if (r.canResolve(manager, req)) {
                winner = challenge(r, req, winner);
            }
        }
        if (winner == null) {
            req.setState(RequestState.REPORTED);
            LOG.log(System.Logger.Level.DEBUG, "No resolver for {0}", req); // REPORTED is a legitimate state
            return;
        }
        resolveWith(req, winner.resolver(), blacklist, winner.children());
    }

    private boolean offeredTo(Resolver r, Request req, Set<String> blacklist) {
        return !blacklist.contains(r.resolverId())
                && !resolvers.isBeingRemoved(r.resolverId())
                && r.handles(req.requestable());
    }

    /**
     * {@code r}'s attempt when it is the first taker, or when it is strictly more suitable than {@code best} (whose
     * children are then cancelled); else {@code best}.
     */
    private Attempt challenge(Resolver r, Request req, Attempt best) {
        if (best == null) {
            Optional<List<Deliverable>> result = r.attemptResolve(manager, req);
            return result.isPresent() ? new Attempt(r, r.suitability(manager, req), createAll(r, result.get())) : null;
        }
        double metric = r.suitability(manager, req);
        if (metric >= best.metric()) {
            return best;
        }
        Optional<List<Deliverable>> result = r.attemptResolve(manager, req);
        if (result.isEmpty()) {
            return best;
        }
        best.children().forEach(canceller::cancel);
        return new Attempt(r, metric, createAll(r, result.get()));
    }

    List<RequestToken> createAll(Resolver requester, List<Deliverable> what) {
        List<RequestToken> tokens = new ArrayList<>(what.size());
        for (Deliverable d : what) {
            tokens.add(store.create(requester.requesterId(), d, -1).token());
        }
        return tokens;
    }

    /** RequestHandler.resolve: register, notify, link and assign children, then IN_PROGRESS. */
    private void resolveWith(Request req, Resolver resolver, Set<String> blacklist, List<RequestToken> children) {
        store.assign(req.token(), resolver);
        req.setState(RequestState.ASSIGNED);
        resolver.onAssigned(manager, req);

        for (RequestToken c : children) {
            Request child = store.request(c);
            if (child != null) {
                child.setParent(req.token());
                req.addChild(c);
            }
        }
        assignUnassigned(children, blacklist);

        if (req.state().ordinal() < RequestState.IN_PROGRESS.ordinal()) {
            req.setState(RequestState.IN_PROGRESS);
            if (req.children().isEmpty()) {
                resolve(req);
            }
        }
    }

    /** Assigns those of {@code tokens} still open and not yet assigned. */
    void assignUnassigned(List<RequestToken> tokens, Set<String> blacklist) {
        for (RequestToken c : tokens) {
            Request child = store.request(c);
            if (child != null && !store.isAssigned(c)) {
                assign(child, blacklist);
            }
        }
    }

    /**
     * ProviderHandler.removeProvider: the requests of the leaving resolvers are reassigned (their children cancelled
     * first), none of the leaving resolvers being a candidate meanwhile.
     */
    void reassignAway(List<Resolver> leaving) {
        Set<String> ids = new HashSet<>();
        leaving.forEach(r -> ids.add(r.resolverId()));
        resolvers.beginRemoval(ids);
        try {
            for (Resolver r : leaving) {
                reassignAll(r, ids);
            }
        } finally {
            resolvers.endRemoval(ids);
        }
    }

    private void reassignAll(Resolver r, Set<String> blacklist) {
        for (RequestToken token : store.tokensAssignedTo(r.resolverId())) {
            Request req = store.request(token);
            if (req != null) {
                canceller.cancelChildren(req);
            }
        }
        for (RequestToken token : store.tokensAssignedTo(r.resolverId())) {
            Request req = store.request(token);
            if (req != null) {
                reassign(req, blacklist);
            }
        }
    }

    /** RequestHandler.reassignRequest. */
    void reassign(Request req, Set<String> blacklist) {
        if (!req.children().isEmpty()) {
            throw new IllegalArgumentException("Can not reassign a request that has children: " + req);
        }
        Resolver current = store.resolverOf(req.token());
        if (current != null) {
            current.onCancelling(manager, req);
            store.unassign(req.token());
            current.onCancelled(manager, req);
        }
        req.setState(RequestState.REPORTED);
        assign(req, blacklist);
    }

    void resolve(Request req) {
        Resolver resolver = store.resolverOf(req.token());
        if (resolver == null
                || req.state() != RequestState.IN_PROGRESS
                || !req.children().isEmpty()) {
            throw new IllegalStateException("Cannot resolve " + req);
        }
        resolver.resolve(manager, req);
    }
}
