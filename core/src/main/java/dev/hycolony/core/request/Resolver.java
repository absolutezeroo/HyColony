package dev.hycolony.core.request;

import java.util.List;
import java.util.Optional;
import java.util.function.Predicate;

/**
 * MineColonies IRequestResolver. A resolver is also the requester of the children it asks for, so its
 * {@link #requesterId()} must be unique among resolvers and distinct from every other requester's id (e.g. a
 * building's): use {@code "resolver:" + resolverId()}. {@link RequestManager} rejects a duplicate.
 */
public interface Resolver extends Requester {
    /** Stable, persisted, e.g. "building:1,64,2" / "player" / "retrying". */
    String resolverId();

    int priority();

    boolean handles(Deliverable requestable);

    boolean canResolve(RequestManager m, Request r);

    /** Empty = cannot resolve; otherwise the children to create (possibly none). */
    Optional<List<Deliverable>> attemptResolve(RequestManager m, Request r);

    void resolve(RequestManager m, Request r);

    default List<Deliverable> followups(RequestManager m, Request r) {
        return List.of();
    }

    default void onAssigned(RequestManager m, Request r) {}

    default void onCancelling(RequestManager m, Request r) {}

    default void onCancelled(RequestManager m, Request r) {}

    default void onColonyUpdate(RequestManager m, Predicate<Request> which) {}

    /** Lower is better. */
    double suitability(RequestManager m, Request r);

    default void tick(RequestManager m) {}
}
