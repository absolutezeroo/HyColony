package dev.hycolony.core.request.resolver;

import dev.hycolony.core.kernel.BlockPos;
import dev.hycolony.core.request.Request;
import dev.hycolony.core.request.RequestManager;
import dev.hycolony.core.request.Resolver;
import dev.hycolony.core.request.model.Deliverable;
import dev.hycolony.core.request.model.RequestToken;
import dev.hycolony.core.request.model.Requestable;
import dev.hycolony.core.request.model.RequesterId;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Predicate;

/**
 * MineColonies StandardRetryingRequestResolver: holds a request for {@link #DELAY_UPDATES} updates, then reassigns it;
 * after {@link #MAX_TRIES} holds it blacklists itself so the request falls through to the player.
 */
public final class RetryingResolver implements Resolver {
    public static final String ID = "retrying";
    public static final int PRIORITY = 50;
    /**
     * MC RETRY_DELAY and getMaximalTries: 1200 updates of the request system, one every
     * {@link RequestManager#TICK_INTERVAL} ticks (13 200 ticks, 11 minutes), per try.
     */
    public static final int DELAY_UPDATES = 1200, MAX_TRIES = 3;

    private static final RequesterId REQUESTER_ID = new RequesterId("resolver:" + ID);

    private final BlockPos location;
    private final Map<RequestToken, Integer> delays = new LinkedHashMap<>();
    /** Survives our own reassignment (onCancelling only drops the delay); pruned at the next tick otherwise. */
    private final Map<RequestToken, Integer> tries = new HashMap<>();

    public RetryingResolver(BlockPos location) {
        this.location = location;
    }

    public Map<RequestToken, Integer> delays() {
        return Collections.unmodifiableMap(delays);
    }

    public Map<RequestToken, Integer> tries() {
        return Collections.unmodifiableMap(tries);
    }

    /** Persistence only. */
    public void restore(Map<RequestToken, Integer> newDelays, Map<RequestToken, Integer> newTries) {
        delays.clear();
        delays.putAll(newDelays);
        tries.clear();
        tries.putAll(newTries);
    }

    @Override
    public String resolverId() {
        return ID;
    }

    @Override
    public int priority() {
        return PRIORITY;
    }

    /** Deliverables only (MC getRequestType = {@code IRetryable}): courier deliveries and pickups are not retried. */
    @Override
    public boolean handles(Requestable requestable) {
        return requestable instanceof Deliverable;
    }

    @Override
    public boolean canResolve(RequestManager m, Request r) {
        return true;
    }

    @Override
    public Optional<List<Requestable>> attemptResolve(RequestManager m, Request r) {
        return Optional.of(List.of());
    }

    @Override
    public double suitability(RequestManager m, Request r) {
        return 0;
    }

    @Override
    public void resolve(RequestManager m, Request r) {
        delays.put(r.token(), DELAY_UPDATES);
        tries.merge(r.token(), 1, Integer::sum);
    }

    @Override
    public void onCancelling(RequestManager m, Request r) {
        delays.remove(r.token());
    }

    /** Called every {@link RequestManager#TICK_INTERVAL} ticks. */
    @Override
    public void tick(RequestManager m) {
        // Reassignments queued last tick have run: whatever did not come back to us is forgotten.
        tries.keySet().retainAll(delays.keySet());
        List<RequestToken> due = new ArrayList<>();
        for (Map.Entry<RequestToken, Integer> e : delays.entrySet()) {
            e.setValue(e.getValue() - 1); // MC update: --current, once per request-system update
            if (e.getValue() <= 0) {
                due.add(e.getKey());
            }
        }
        for (RequestToken t : due) {
            if (canReassign(m, t)) {
                delays.remove(t);
                m.reassign(t, tries.getOrDefault(t, 0) >= MAX_TRIES ? Set.of(ID) : Set.of());
            } else {
                // Deviation from MC: MC's reassignment throws on a request with children and the resolver forgets
                // it; here it waits another delay for its children instead of being stranded.
                delays.put(t, DELAY_UPDATES);
            }
        }
    }

    /**
     * MC onColonyUpdate: matching requests are reassigned now, with the retrying resolver blacklisted; for one that
     * does not match, the first matching ancestor is ({@link Ancestors}).
     */
    @Override
    public void onColonyUpdate(RequestManager m, Predicate<Request> which) {
        for (RequestToken t : new ArrayList<>(delays.keySet())) {
            Optional<Request> r = m.get(t);
            if (r.isEmpty()) {
                continue;
            }
            if (!which.test(r.get())) {
                Ancestors.reassignMatching(m, r.get(), which);
            } else if (canReassign(m, t)) {
                m.reassign(t, Set.of(ID));
            }
        }
    }

    /** A request with children cannot be reassigned (MineColonies' reassignment threw and it was forgotten). */
    private static boolean canReassign(RequestManager m, RequestToken t) {
        return m.get(t).map(r -> r.children().isEmpty()).orElse(false);
    }

    @Override
    public RequesterId requesterId() {
        return REQUESTER_ID;
    }

    @Override
    public BlockPos location() {
        return location;
    }

    @Override
    public String displayName() {
        return "Player";
    }

    @Override
    public void onRequestComplete(RequestManager manager, Request request) {}

    @Override
    public void onRequestCancelled(RequestManager manager, Request request) {}
}
