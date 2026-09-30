package dev.hycolony.core.request.resolver;

import dev.hycolony.core.request.Request;
import dev.hycolony.core.request.RequestManager;
import dev.hycolony.core.request.model.RequestState;
import dev.hycolony.core.request.model.RequestToken;
import java.util.Optional;
import java.util.function.Predicate;

/**
 * The ancestor walk of MC StandardPlayerRequestResolver and StandardRetryingRequestResolver.onColonyUpdate: a request
 * a colony update does not match may be waiting for an ingredient of one that does.
 */
final class Ancestors {
    /**
     * Deeper than any real chain (MC MAX_CRAFTING_CYCLE_DEPTH is 20). Deviation from MC: MC walks unbounded; this
     * bounds the walk should a saved cycle slip through.
     */
    private static final int MAX_DEPTH = 64;

    private Ancestors() {}

    /**
     * Reassigns the first ancestor of {@code r} that {@code which} matches, as MC does (its caller is blacklisted, so
     * the ancestor never comes back to it and MC's walk stops there too); nothing if none matches. Deviation from MC:
     * MC cancels the ancestor's children directly and reassigns it with the caller blacklisted. Here its first child
     * is cancelled, so the cascade of onChildRequestCancelled cancels the others, resets the ancestor's deliveries
     * and reassigns it without a blacklist. A missing ancestor ends the walk (MC's retrying resolver would throw).
     */
    static void reassignMatching(RequestManager m, Request r, Predicate<Request> which) {
        Optional<RequestToken> parent = r.parent();
        for (int depth = 0; parent.isPresent() && depth < MAX_DEPTH; depth++) {
            Request ancestor = m.get(parent.get()).orElse(null);
            if (ancestor == null) {
                return;
            }
            if (which.test(ancestor)) {
                // never childless: the child the walk came through is still one of its children
                m.updateState(ancestor.children().getFirst(), RequestState.CANCELLED);
                return;
            }
            parent = ancestor.parent();
        }
    }
}
