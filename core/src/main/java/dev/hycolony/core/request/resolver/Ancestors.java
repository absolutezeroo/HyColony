package dev.hycolony.core.request.resolver;

import dev.hycolony.core.request.Request;
import dev.hycolony.core.request.RequestManager;
import dev.hycolony.core.request.model.RequestState;
import dev.hycolony.core.request.model.RequestToken;
import java.util.Optional;
import java.util.Set;
import java.util.function.Predicate;

/**
 * The ancestor walk of MC StandardPlayerRequestResolver and StandardRetryingRequestResolver.onColonyUpdate: a request
 * a colony update does not match may be waiting for an ingredient of one that does.
 */
final class Ancestors {
    /** Deeper than any real chain (MC MAX_CRAFTING_CYCLE_DEPTH is 20): a bound should a saved cycle slip through. */
    private static final int MAX_DEPTH = 64;

    private Ancestors() {}

    /**
     * Reassigns the first ancestor of {@code r} that {@code which} matches; nothing if none matches. Deviation from
     * MC: MC cancels the ancestor's children directly and reassigns it with {@code resolverId} blacklisted, walking on
     * when it comes back. Here one child is cancelled, so the cascade of onChildRequestCancelled cancels its siblings
     * and reassigns the ancestor with no blacklist (the reassignment is queued, so the walk stops there).
     */
    static void reassignMatching(RequestManager m, Request r, Predicate<Request> which, String resolverId) {
        Optional<RequestToken> parent = r.parent();
        for (int depth = 0; parent.isPresent() && depth < MAX_DEPTH; depth++) {
            Request ancestor = m.get(parent.get()).orElse(null);
            if (ancestor == null) {
                return;
            }
            if (which.test(ancestor)) {
                if (ancestor.children().isEmpty()) {
                    m.reassign(ancestor.token(), Set.of(resolverId));
                } else {
                    m.updateState(ancestor.children().get(0), RequestState.CANCELLED);
                }
                return;
            }
            parent = ancestor.parent();
        }
    }
}
