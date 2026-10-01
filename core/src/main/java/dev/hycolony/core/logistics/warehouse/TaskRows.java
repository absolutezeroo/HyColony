package dev.hycolony.core.logistics.warehouse;

import dev.hycolony.core.colony.Colony;
import dev.hycolony.core.kernel.BlockPos;
import dev.hycolony.core.request.Request;
import dev.hycolony.core.request.model.Crafting;
import dev.hycolony.core.request.model.Deliverable;
import dev.hycolony.core.request.model.Delivery;
import dev.hycolony.core.request.model.Pickup;
import dev.hycolony.core.request.model.RequestState;
import dev.hycolony.core.request.model.RequestToken;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/** The rows of a courier task list (MC WindowHutRequestTaskModule), for the warehouse and courier hut tabs. */
public final class TaskRows {
    private TaskRows() {}

    /** One row per token still known, in order (MC drops the tokens whose request is gone). */
    public static List<TaskRow> of(Colony c, List<RequestToken> tokens) {
        return tokens.stream()
                .flatMap(t -> c.requests().get(t).stream())
                .map(r -> row(c, r))
                .toList();
    }

    private static TaskRow row(Colony c, Request r) {
        Optional<Request> parent = forRequester(c, r);
        return new TaskRow(
                r.token(),
                r.requestable(),
                RequesterLocation.displayName(c, r),
                parent.map(p -> RequesterLocation.displayName(c, p)),
                RequesterLocation.of(c, r.requester()),
                parent.flatMap(p -> RequesterLocation.of(c, p.requester())),
                priority(r),
                r.state() == RequestState.IN_PROGRESS);
    }

    /**
     * MC WindowHutRequestTaskModule: climbs the parents while they ask from the same place as {@code r}, and returns
     * the one reached; empty without a parent. Stops before a request already visited: loading already
     * drops parent cycles ({@code SavedRequests}), so this is only a cheap safety net.
     */
    private static Optional<Request> forRequester(Colony c, Request r) {
        Request parent = r.parent().flatMap(c.requests()::get).orElse(null);
        Optional<BlockPos> here = RequesterLocation.of(c, r.requester());
        Set<RequestToken> visited = new HashSet<>(List.of(r.token()));
        while (parent != null
                && visited.add(parent.token())
                && parent.parent().isPresent()
                && RequesterLocation.of(c, parent.requester()).equals(here)) {
            Request up = c.requests().get(parent.parent().get()).orElse(null);
            if (up == null || visited.contains(up.token())) {
                break;
            }
            parent = up;
        }
        return Optional.ofNullable(parent);
    }

    /** MC IDeliverymanRequestable.getPriority; 0 for any other request. */
    private static int priority(Request r) {
        return switch (r.requestable()) {
            case Delivery d -> d.priority();
            case Pickup p -> p.priority();
            case Deliverable _, Crafting _ -> 0;
        };
    }
}
