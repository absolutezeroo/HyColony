package dev.hycolony.core.request;

import dev.hycolony.core.request.model.RequestState;
import dev.hycolony.core.request.model.RequestToken;

/** Test double: request states no public call makes, for the invariant checks to find. */
public final class BrokenRequests {
    private BrokenRequests() {}

    /** Forgets {@code token}'s resolver, its state left as is. */
    public static void dropResolver(RequestManager requests, RequestToken token) {
        requests.store().unassign(token);
    }

    /** Sets {@code token}'s state without its transition. */
    public static void setState(RequestManager requests, RequestToken token, RequestState state) {
        requests.store().require(token).setState(state);
    }
}
