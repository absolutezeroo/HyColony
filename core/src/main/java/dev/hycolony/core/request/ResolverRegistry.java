package dev.hycolony.core.request;

import dev.hycolony.core.request.model.RequesterId;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

/**
 * The resolvers a request manager knows (MC ResolverHandler + ProviderHandler), sorted by priority and indexed by
 * resolver id, by their own requester id and by provider.
 */
final class ResolverRegistry {
    private static final System.Logger LOG = System.getLogger(RequestManager.class.getName());

    private final RequesterRegistry requesters;
    /** Sorted by priority descending, registration order within a priority. */
    private final List<Resolver> resolvers = new ArrayList<>();
    /** The resolvers offered every request ({@link Resolver#servesOnly()} empty), sorted the same way. */
    private final List<Resolver> shared = new ArrayList<>();
    /** The others, by the one requester they serve (a building's own resolver): an O(1) lookup per assignment. */
    private final Map<RequesterId, List<Resolver>> ownResolvers = new HashMap<>();

    private final Map<String, Resolver> resolversById = new HashMap<>();
    private final Map<RequesterId, Resolver> resolversByRequesterId = new HashMap<>();
    private final Map<String, List<Resolver>> providers = new HashMap<>();
    /** Resolvers of a provider being removed: never candidates (MineColonies' tempBlackList). */
    private final Set<String> beingRemoved = new HashSet<>();

    ResolverRegistry(RequesterRegistry requesters) {
        this.requesters = Objects.requireNonNull(requesters, "requesters");
    }

    void register(Resolver r) {
        checkRegistrable(r);
        resolversById.put(r.resolverId(), r);
        insertByPriority(resolvers, r);
        Optional<RequesterId> only = r.servesOnly();
        insertByPriority(only.isEmpty() ? shared : ownResolvers.computeIfAbsent(only.get(), k -> new ArrayList<>()), r);
        resolversByRequesterId.put(r.requesterId(), r);
    }

    void addProvider(ResolverProvider p) {
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
                throw new IllegalArgumentException(
                        "Duplicate resolver in provider " + p.providerId() + ": " + r.resolverId());
            }
        }
        list.forEach(this::register);
        providers.put(p.providerId(), list);
    }

    /** Resolver ids and requester ids must be unique; a requester id must not belong to a registry requester. */
    private void checkRegistrable(Resolver r) {
        if (resolversById.containsKey(r.resolverId())) {
            throw new IllegalArgumentException("Resolver already registered: " + r.resolverId());
        }
        if (resolversByRequesterId.containsKey(r.requesterId())
                || requesters.find(r.requesterId()).isPresent()) {
            throw new IllegalArgumentException(
                    "Requester id already in use: " + r.requesterId().value() + " (resolver " + r.resolverId() + ")");
        }
    }

    /** After every resolver of the same or a higher priority. */
    private static void insertByPriority(List<Resolver> list, Resolver r) {
        int i = 0;
        while (i < list.size() && list.get(i).priority() >= r.priority()) {
            i++;
        }
        list.add(i, r);
    }

    Optional<List<Resolver>> provider(String providerId) {
        return Optional.ofNullable(providers.get(providerId));
    }

    /** Unregisters the provider and its resolvers (their requests were reassigned first). */
    void removeProvider(String providerId, List<Resolver> list) {
        for (Resolver r : list) {
            resolvers.remove(r);
            shared.remove(r);
            r.servesOnly().ifPresent(only -> {
                List<Resolver> own = ownResolvers.get(only);
                if (own != null && own.remove(r) && own.isEmpty()) {
                    ownResolvers.remove(only);
                }
            });
            resolversById.remove(r.resolverId());
            resolversByRequesterId.remove(r.requesterId(), r);
        }
        providers.remove(providerId);
    }

    /** From now until {@link #endRemoval}, these resolvers are never candidates. */
    void beginRemoval(Set<String> resolverIds) {
        beingRemoved.addAll(resolverIds);
    }

    void endRemoval(Set<String> resolverIds) {
        beingRemoved.removeAll(resolverIds);
    }

    boolean isBeingRemoved(String resolverId) {
        return beingRemoved.contains(resolverId);
    }

    /** The resolvers a request of {@code requester} is offered, by priority. */
    List<Resolver> candidates(RequesterId requester) {
        List<Resolver> own = ownResolvers.get(requester);
        if (own == null) {
            return shared;
        }
        List<Resolver> all = new ArrayList<>(shared.size() + own.size());
        all.addAll(shared);
        own.forEach(r -> insertByPriority(all, r));
        return all;
    }

    /** Every resolver, by priority: a copy, so a resolver may (un)register others while it is called. */
    List<Resolver> snapshot() {
        return List.copyOf(resolvers);
    }

    Optional<Resolver> byId(String resolverId) {
        return Optional.ofNullable(resolversById.get(resolverId));
    }

    /** A registry requester (a building...) or a resolver (the requester of the children it asked for). */
    boolean knowsRequester(RequesterId id) {
        return requesters.find(id).isPresent() || resolversByRequesterId.containsKey(id);
    }

    /** Buildings etc. via the registry first, then resolvers; a missing one is logged. */
    Optional<Requester> requester(Request req) {
        Optional<Requester> found = requesters.find(req.requester());
        if (found.isEmpty()) {
            found = Optional.ofNullable(resolversByRequesterId.get(req.requester()));
        }
        if (found.isEmpty()) {
            LOG.log(
                    System.Logger.Level.WARNING,
                    "Requester {0} not found for {1}",
                    req.requester().value(),
                    req);
        }
        return found;
    }
}
