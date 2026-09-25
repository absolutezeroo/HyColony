package dev.hycolony.core.request;

import dev.hycolony.core.kernel.item.ItemAmount;
import dev.hycolony.core.kernel.port.ItemCatalog;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.function.Predicate;

/**
 * One per colony. Port of MineColonies' StandardRequestManager + RequestHandler/ResolverHandler/ProviderHandler,
 * with the data stores collapsed into direct maps (plus the request -> resolver inverse index).
 *
 * <p>Every public mutation is queued and processed FIFO by a non-reentrant loop: a callback (requester or
 * resolver) that calls back into the manager only enqueues, and its work runs after the current step. Internal
 * cascades (child completion, cancellation of a subtree, ...) keep MineColonies' synchronous callback order.
 */
public final class RequestManager {
    /** The colony ticks the manager every 11 game ticks (MineColonies' request system rate). */
    public static final int TICK_INTERVAL = 11;
    private static final System.Logger LOG = System.getLogger(RequestManager.class.getName());
    private static final Set<RequestState> PUBLIC_STATES = EnumSet.of(RequestState.RESOLVED, RequestState.COMPLETED,
            RequestState.CANCELLED, RequestState.FAILED, RequestState.RECEIVED);

    private final RequesterRegistry requesters;
    private final ItemCatalog catalog;

    /** Sorted by priority descending, registration order within a priority. */
    private final List<Resolver> resolvers = new ArrayList<>();
    private final Map<String, Resolver> resolversById = new HashMap<>();
    private final Map<RequesterId, Resolver> resolversByRequesterId = new HashMap<>();
    private final Map<String, List<Resolver>> providers = new HashMap<>();
    /** Resolvers of a provider being removed: never candidates (MineColonies' tempBlackList). */
    private final Set<String> beingRemoved = new HashSet<>();

    private final Map<RequestToken, Request> requests = new LinkedHashMap<>();
    private final Map<RequestToken, Resolver> resolverOf = new HashMap<>();
    private final Map<String, Set<RequestToken>> assigned = new HashMap<>();
    private final Map<RequesterId, Set<RequestToken>> byRequester = new HashMap<>();

    private final ArrayDeque<Runnable> queue = new ArrayDeque<>();
    private boolean processing;

    public RequestManager(RequesterRegistry requesters, ItemCatalog catalog) {
        this.requesters = Objects.requireNonNull(requesters, "requesters");
        this.catalog = Objects.requireNonNull(catalog, "catalog");
    }

    // ------------------------------------------------------------------ resolvers & providers

    /** Player, retrying: never removed. */
    public void registerBuiltIn(Resolver r) {
        register(r);
    }

    public void onProviderAdded(ResolverProvider p) {
        if (providers.containsKey(p.providerId())) {
            throw new IllegalArgumentException("Provider already registered: " + p.providerId());
        }
        List<Resolver> list = List.copyOf(p.resolvers());
        // Validate everything first, so a failure leaves nothing partially registered.
        Set<String> ids = new HashSet<>();
        Set<RequesterId> requesterIds = new HashSet<>();
        for (Resolver r : list) {
            checkRegistrable(r);
            if (!ids.add(r.resolverId()) || !requesterIds.add(r.requesterId())) {
                throw new IllegalArgumentException("Duplicate resolver in provider " + p.providerId() + ": "
                        + r.resolverId());
            }
        }
        list.forEach(this::register);
        providers.put(p.providerId(), list);
    }

    /** Reassigns its requests with all its resolvers blacklisted, then drops them (ProviderHandler.removeProvider). */
    public void onProviderRemoved(ResolverProvider p) {
        submit(() -> removeProvider(p.providerId()));
    }

    /** Resolver ids and requester ids must be unique; a requester id must not belong to a registry requester. */
    private void checkRegistrable(Resolver r) {
        if (resolversById.containsKey(r.resolverId())) {
            throw new IllegalArgumentException("Resolver already registered: " + r.resolverId());
        }
        if (resolversByRequesterId.containsKey(r.requesterId()) || requesters.find(r.requesterId()).isPresent()) {
            throw new IllegalArgumentException("Requester id already in use: " + r.requesterId().value()
                    + " (resolver " + r.resolverId() + ")");
        }
    }

    private void register(Resolver r) {
        checkRegistrable(r);
        resolversById.put(r.resolverId(), r);
        int i = 0;
        while (i < resolvers.size() && resolvers.get(i).priority() >= r.priority()) {
            i++;
        }
        resolvers.add(i, r);
        resolversByRequesterId.put(r.requesterId(), r);
    }

    private void removeProvider(String providerId) {
        List<Resolver> list = providers.get(providerId);
        if (list == null) {
            return;
        }
        Set<String> ids = new HashSet<>();
        list.forEach(r -> ids.add(r.resolverId()));
        beingRemoved.addAll(ids);
        try {
            for (Resolver r : list) {
                for (RequestToken token : tokensAssignedTo(r.resolverId())) {
                    Request req = requests.get(token);
                    if (req != null) {
                        new ArrayList<>(req.children()).forEach(this::cancelDirectly);
                    }
                }
                for (RequestToken token : tokensAssignedTo(r.resolverId())) {
                    Request req = requests.get(token);
                    if (req != null) {
                        reassignNow(req, ids);
                    }
                }
            }
        } finally {
            beingRemoved.removeAll(ids);
        }
        for (Resolver r : list) {
            resolvers.remove(r);
            resolversById.remove(r.resolverId());
            resolversByRequesterId.remove(r.requesterId(), r);
        }
        providers.remove(providerId);
    }

    // ------------------------------------------------------------------ public mutations (queued)

    public RequestToken createAndAssign(Requester requester, Deliverable what, int citizenId) {
        Request req = create(requester.requesterId(), what, citizenId);
        submit(() -> {
            if (requests.containsKey(req.token()) && !resolverOf.containsKey(req.token())) {
                assignNow(req, Set.of());
            }
        });
        return req.token();
    }

    /** A child created by a resolver for one of its requests; inherits the parent's blacklist. */
    public RequestToken createChild(Resolver parentResolver, RequestToken parent, Deliverable what) {
        Request p = require(parent);
        Request child = create(parentResolver.requesterId(), what, -1);
        child.setParent(parent);
        p.addChild(child.token());
        submit(() -> {
            if (requests.containsKey(child.token()) && !resolverOf.containsKey(child.token())) {
                assignNow(child, p.blacklist());
            }
        });
        return child.token();
    }

    public void assign(RequestToken token, Set<String> blacklist) {
        require(token);
        Set<String> bl = Set.copyOf(blacklist);
        submit(() -> {
            Request req = requests.get(token);
            if (req != null) {
                if (resolverOf.containsKey(token)) {
                    throw new IllegalArgumentException("Request already assigned: " + token);
                }
                assignNow(req, bl);
            }
        });
    }

    public void reassign(RequestToken token, Set<String> blacklist) {
        require(token);
        Set<String> bl = Set.copyOf(blacklist);
        submit(() -> {
            Request req = requests.get(token);
            if (req != null) {
                reassignNow(req, bl);
            }
        });
    }

    /** Public transitions only: RESOLVED, COMPLETED, CANCELLED, FAILED, RECEIVED (use {@link #overrule}). */
    public void updateState(RequestToken token, RequestState newState) {
        if (!PUBLIC_STATES.contains(newState)) {
            throw new IllegalArgumentException("Not a public transition: " + newState);
        }
        require(token);
        submit(() -> {
            Request req = requests.get(token);
            if (req != null) {
                transition(req, newState);
            }
        });
    }

    /** The player provided the items: cancel children, then COMPLETED. Applied once (MineColonies ran it twice). */
    public void overrule(RequestToken token, List<ItemAmount> delivered) {
        require(token);
        List<ItemAmount> items = List.copyOf(delivered);
        submit(() -> {
            Request req = requests.get(token);
            if (req != null && req.state().ordinal() < RequestState.COMPLETED.ordinal()) {
                overruleNow(req, items);
            }
        });
    }

    public void onColonyUpdate(Predicate<Request> which) {
        submit(() -> List.copyOf(resolvers).forEach(r -> r.onColonyUpdate(this, which)));
    }

    /** Ticks every registered resolver. */
    public void tick() {
        submit(() -> List.copyOf(resolvers).forEach(r -> r.tick(this)));
    }

    public void addDelivery(RequestToken token, ItemAmount amount) {
        require(token).addDelivery(amount);
    }

    // ------------------------------------------------------------------ queries

    public Optional<Request> get(RequestToken token) {
        return Optional.ofNullable(requests.get(token));
    }

    /** O(1): inverse index. */
    public Optional<Resolver> resolverOf(RequestToken token) {
        return Optional.ofNullable(resolverOf.get(token));
    }

    public List<Request> byRequester(RequesterId id) {
        return toRequests(byRequester.get(id));
    }

    public List<Request> assignedTo(String resolverId) {
        return toRequests(assigned.get(resolverId));
    }

    public Collection<Request> all() {
        return Collections.unmodifiableCollection(requests.values());
    }

    public ItemCatalog catalog() {
        return catalog;
    }

    public Optional<Resolver> resolver(String resolverId) {
        return Optional.ofNullable(resolversById.get(resolverId));
    }

    // ------------------------------------------------------------------ persistence (RequestSerializer)

    Map<String, Set<RequestToken>> assignments() {
        return Collections.unmodifiableMap(assigned);
    }

    void restore(Request req) {
        requests.put(req.token(), req);
        byRequester.computeIfAbsent(req.requester(), k -> new LinkedHashSet<>()).add(req.token());
    }

    void restoreAssignment(RequestToken token, Resolver resolver) {
        if (requests.containsKey(token)) {
            resolverOf.put(token, resolver);
            assigned.computeIfAbsent(resolver.resolverId(), k -> new LinkedHashSet<>()).add(token);
        }
    }

    /** A loaded request whose resolver no longer exists: reassigned rather than dropped (MineColonies dropped it). */
    void reassignLoaded(RequestToken token) {
        submit(() -> {
            Request req = requests.get(token);
            if (req != null) {
                new ArrayList<>(req.children()).forEach(this::cancelDirectly);
                reassignNow(req, req.blacklist());
            }
        });
    }

    // ------------------------------------------------------------------ queue

    private void submit(Runnable op) {
        queue.addLast(op);
        if (processing) {
            return;
        }
        processing = true;
        boolean ok = false;
        try {
            Runnable next;
            while ((next = queue.pollFirst()) != null) {
                next.run();
            }
            ok = true;
        } finally {
            processing = false;
            if (!ok && !queue.isEmpty()) {
                // Never replay them inside an unrelated later call.
                LOG.log(System.Logger.Level.WARNING, "An operation failed; dropping {0} queued request operation(s)",
                        queue.size());
                queue.clear();
            }
        }
    }

    // ------------------------------------------------------------------ assignment (RequestHandler.assignRequestDefault)

    private Request create(RequesterId requester, Deliverable what, int citizenId) {
        Request req = new Request(RequestToken.random(), requester, what, citizenId);
        requests.put(req.token(), req);
        byRequester.computeIfAbsent(requester, k -> new LinkedHashSet<>()).add(req.token());
        return req;
    }

    private void assignNow(Request req, Set<String> blacklist) {
        req.setBlacklist(blacklist);
        req.setState(RequestState.ASSIGNING);

        Resolver winner = null;
        double winnerMetric = Double.MAX_VALUE;
        List<RequestToken> attempt = List.of();
        for (Resolver r : resolvers) {
            if (blacklist.contains(r.resolverId()) || beingRemoved.contains(r.resolverId())
                    || !r.handles(req.requestable())) {
                continue;
            }
            if (winner != null && winner.priority() != r.priority()) {
                break;
            }
            if (!r.canResolve(this, req)) {
                continue;
            }
            if (winner == null) {
                Optional<List<Deliverable>> result = r.attemptResolve(this, req);
                if (result.isPresent()) {
                    winner = r;
                    winnerMetric = r.suitability(this, req);
                    attempt = createAll(r, result.get());
                }
            } else {
                double metric = r.suitability(this, req);
                if (metric < winnerMetric) {
                    Optional<List<Deliverable>> result = r.attemptResolve(this, req);
                    if (result.isPresent()) {
                        attempt.forEach(this::cancelDirectly);
                        winner = r;
                        winnerMetric = metric;
                        attempt = createAll(r, result.get());
                    }
                }
            }
        }

        if (winner == null) {
            req.setState(RequestState.REPORTED);
            LOG.log(System.Logger.Level.DEBUG, "No resolver for {0}", req); // REPORTED is a legitimate state
            return;
        }
        resolveWith(req, winner, blacklist, attempt);
    }

    private List<RequestToken> createAll(Resolver requester, List<Deliverable> what) {
        List<RequestToken> tokens = new ArrayList<>(what.size());
        for (Deliverable d : what) {
            tokens.add(create(requester.requesterId(), d, -1).token());
        }
        return tokens;
    }

    /** RequestHandler.resolve: register, notify, link and assign children, then IN_PROGRESS. */
    private void resolveWith(Request req, Resolver resolver, Set<String> blacklist, List<RequestToken> children) {
        resolverOf.put(req.token(), resolver);
        assigned.computeIfAbsent(resolver.resolverId(), k -> new LinkedHashSet<>()).add(req.token());
        req.setState(RequestState.ASSIGNED);
        resolver.onAssigned(this, req);

        for (RequestToken c : children) {
            Request child = requests.get(c);
            if (child != null) {
                child.setParent(req.token());
                req.addChild(c);
            }
        }
        for (RequestToken c : children) {
            Request child = requests.get(c);
            if (child != null && !resolverOf.containsKey(c)) {
                assignNow(child, blacklist);
            }
        }

        if (req.state().ordinal() < RequestState.IN_PROGRESS.ordinal()) {
            req.setState(RequestState.IN_PROGRESS);
            if (req.children().isEmpty()) {
                resolveNow(req);
            }
        }
    }

    /** RequestHandler.reassignRequest. */
    private void reassignNow(Request req, Set<String> blacklist) {
        if (!req.children().isEmpty()) {
            throw new IllegalArgumentException("Can not reassign a request that has children: " + req);
        }
        Resolver current = resolverOf.get(req.token());
        if (current != null) {
            current.onCancelling(this, req);
            unassign(req.token());
            current.onCancelled(this, req);
        }
        req.setState(RequestState.REPORTED);
        assignNow(req, blacklist);
    }

    private void resolveNow(Request req) {
        Resolver resolver = resolverOf.get(req.token());
        if (resolver == null || req.state() != RequestState.IN_PROGRESS || !req.children().isEmpty()) {
            throw new IllegalStateException("Cannot resolve " + req);
        }
        resolver.resolve(this, req);
    }

    // ------------------------------------------------------------------ transitions (StandardRequestManager.updateRequestState)

    private void transition(Request req, RequestState state) {
        req.setState(state);
        switch (state) {
            case RESOLVED -> onResolved(req);
            case COMPLETED -> onCompleted(req);
            case CANCELLED, FAILED -> onCancelled(req);
            case RECEIVED -> clean(req.token());
            default -> { }
        }
    }

    private void onResolved(Request req) {
        Resolver resolver = resolverOf.get(req.token());
        List<Deliverable> followups = resolver == null ? List.of() : resolver.followups(this, req);
        req.setState(RequestState.FOLLOWUP_IN_PROGRESS);
        if (!followups.isEmpty()) {
            List<RequestToken> tokens = createAll(resolver, followups);
            for (RequestToken c : tokens) {
                requests.get(c).setParent(req.token());
                req.addChild(c);
            }
            for (RequestToken c : tokens) {
                Request child = requests.get(c);
                if (child != null && !resolverOf.containsKey(c)) {
                    assignNow(child, Set.of());
                }
            }
        }
        if (req.children().isEmpty()) {
            transition(req, RequestState.COMPLETED);
        }
    }

    private void onCompleted(Request req) {
        requester(req).ifPresent(r -> r.onRequestComplete(this, req));
        Request parent = req.parent().map(requests::get).orElse(null);
        if (parent == null) {
            return;
        }
        transition(req, RequestState.RECEIVED);
        parent.removeChild(req.token());
        req.setParent(null);
        if (parent.children().isEmpty()) {
            if (parent.state() == RequestState.IN_PROGRESS) {
                resolveNow(parent);
            } else if (parent.state() == RequestState.FOLLOWUP_IN_PROGRESS) {
                transition(parent, RequestState.COMPLETED);
            }
        }
    }

    /** RequestHandler.onRequestOverruled, preceded by overrideCurrentDeliveries. */
    private void overruleNow(Request req, List<ItemAmount> delivered) {
        req.setState(RequestState.OVERRULED);
        Resolver resolver = resolverOf.get(req.token());
        if (resolver == null) {
            clean(req.token());
            return;
        }
        new ArrayList<>(req.children()).forEach(this::cancelDirectly);
        resolver.onCancelling(this, req);
        if (!delivered.isEmpty()) {
            req.setDeliveries(delivered);
        }
        transition(req, RequestState.COMPLETED);
        resolver.onCancelled(this, req);
    }

    private void onCancelled(Request req) {
        Request parent = req.parent().map(requests::get).orElse(null);
        if (parent == null) {
            cancelDirectly(req.token());
            return;
        }
        // onChildRequestCancelled; the parent keeps the blacklist it was assigned with.
        parent.setDeliveries(List.of());
        new ArrayList<>(parent.children()).forEach(this::cancelDirectly);
        reassignNow(parent, parent.blacklist());
    }

    /** onRequestCancelledDirectly: the subtree first, then this request; requester notified; cleaned. */
    private void cancelDirectly(RequestToken token) {
        Request req = requests.get(token);
        if (req == null) {
            return;
        }
        new ArrayList<>(req.children()).forEach(this::cancelDirectly);

        Resolver resolver = resolverOf.get(token);
        if (resolver != null) {
            resolver.onCancelling(this, req);
            unassign(token);
        }
        req.parent().map(requests::get).ifPresent(p -> p.removeChild(token));
        req.setParent(null);
        req.setState(RequestState.CANCELLED);
        if (resolver != null) {
            resolver.onCancelled(this, req);
        }
        requester(req).ifPresent(r -> r.onRequestCancelled(this, req));
        clean(token);
    }

    /** cleanRequestData: drop the request from every index. */
    private void clean(RequestToken token) {
        Request req = requests.remove(token);
        if (req == null) {
            return;
        }
        unassign(token);
        Set<RequestToken> mine = byRequester.get(req.requester());
        if (mine != null) {
            mine.remove(token);
            if (mine.isEmpty()) {
                byRequester.remove(req.requester());
            }
        }
    }

    private void unassign(RequestToken token) {
        Resolver r = resolverOf.remove(token);
        if (r == null) {
            return;
        }
        Set<RequestToken> set = assigned.get(r.resolverId());
        if (set != null) {
            set.remove(token);
            if (set.isEmpty()) {
                assigned.remove(r.resolverId());
            }
        }
    }

    // ------------------------------------------------------------------ helpers

    private Request require(RequestToken token) {
        Request req = requests.get(token);
        if (req == null) {
            throw new IllegalArgumentException("Unknown request: " + token);
        }
        return req;
    }

    /** Buildings etc. via the registry first, then resolvers (requesters of the children they asked for). */
    public Optional<Requester> requester(Request req) {
        Optional<Requester> found = requesters.find(req.requester());
        if (found.isEmpty()) {
            found = Optional.ofNullable(resolversByRequesterId.get(req.requester()));
        }
        if (found.isEmpty()) {
            LOG.log(System.Logger.Level.WARNING, "Requester {0} not found for {1}", req.requester().value(), req);
        }
        return found;
    }

    private List<RequestToken> tokensAssignedTo(String resolverId) {
        Set<RequestToken> set = assigned.get(resolverId);
        return set == null ? List.of() : new ArrayList<>(set);
    }

    private List<Request> toRequests(Set<RequestToken> tokens) {
        if (tokens == null) {
            return List.of();
        }
        List<Request> out = new ArrayList<>(tokens.size());
        for (RequestToken t : tokens) {
            out.add(requests.get(t));
        }
        return out;
    }
}
