package dev.hycolony.core.request;

import dev.hycolony.core.request.model.Deliverable;
import dev.hycolony.core.request.model.RequestToken;
import dev.hycolony.core.request.model.RequesterId;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Consumer;

/**
 * A colony's requests, by token, with MC's data store indexes collapsed into maps: the requests of each requester,
 * and the resolver each request is assigned to (both ways).
 */
final class RequestStore {
    private final Map<RequestToken, Request> requests = new LinkedHashMap<>();
    private final Map<RequestToken, Resolver> resolverOf = new HashMap<>();
    private final Map<String, Set<RequestToken>> assigned = new HashMap<>();
    private final Map<RequesterId, Set<RequestToken>> byRequester = new HashMap<>();
    /** Sees every request as it is created, even one closed within the same tick (simulations, debugging). */
    private Consumer<Request> creationListener = r -> {};

    void setCreationListener(Consumer<Request> listener) {
        creationListener = Objects.requireNonNull(listener, "listener");
    }

    Request create(RequesterId requester, Deliverable what, int citizenId) {
        Request req = new Request(RequestToken.random(), requester, what, citizenId);
        requests.put(req.token(), req);
        byRequester.computeIfAbsent(requester, k -> new LinkedHashSet<>()).add(req.token());
        creationListener.accept(req);
        return req;
    }

    /** The request, or null once it is cleaned (the manager's internal, allocation-free lookup). */
    Request request(RequestToken token) {
        return requests.get(token);
    }

    Request require(RequestToken token) {
        Request req = requests.get(token);
        if (req == null) {
            throw new IllegalArgumentException("Unknown request: " + token);
        }
        return req;
    }

    boolean contains(RequestToken token) {
        return requests.containsKey(token);
    }

    /** The assigned resolver, or null. */
    Resolver resolverOf(RequestToken token) {
        return resolverOf.get(token);
    }

    boolean isAssigned(RequestToken token) {
        return resolverOf.containsKey(token);
    }

    void assign(RequestToken token, Resolver resolver) {
        resolverOf.put(token, resolver);
        assigned.computeIfAbsent(resolver.resolverId(), k -> new LinkedHashSet<>())
                .add(token);
    }

    void unassign(RequestToken token) {
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

    /** cleanRequestData: drop the request from every index. */
    void clean(RequestToken token) {
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

    Collection<Request> all() {
        return Collections.unmodifiableCollection(requests.values());
    }

    List<Request> byRequester(RequesterId id) {
        return toRequests(byRequester.get(id));
    }

    /** A copy: cancelling them changes the index. */
    List<RequestToken> tokensOf(RequesterId id) {
        Set<RequestToken> set = byRequester.get(id);
        return set == null ? List.of() : new ArrayList<>(set);
    }

    List<Request> assignedTo(String resolverId) {
        return toRequests(assigned.get(resolverId));
    }

    /** A copy: reassigning them changes the index. */
    List<RequestToken> tokensAssignedTo(String resolverId) {
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

    Map<String, Set<RequestToken>> assignments() {
        return Collections.unmodifiableMap(assigned);
    }

    void restore(Request req) {
        requests.put(req.token(), req);
        byRequester.computeIfAbsent(req.requester(), k -> new LinkedHashSet<>()).add(req.token());
    }

    void restoreAssignment(RequestToken token, Resolver resolver) {
        if (requests.containsKey(token)) {
            assign(token, resolver);
        }
    }
}
