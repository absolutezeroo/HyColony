package dev.hycolony.core.app.view;

import dev.hycolony.core.app.ui.RequestsView;
import dev.hycolony.core.colony.Colony;
import dev.hycolony.core.colony.ColonyContext;
import dev.hycolony.core.kernel.BlockPos;
import dev.hycolony.core.kernel.item.ItemKey;
import dev.hycolony.core.logistics.warehouse.RequesterLocation;
import dev.hycolony.core.request.Request;
import dev.hycolony.core.request.RequestManager;
import dev.hycolony.core.request.Resolver;
import dev.hycolony.core.request.model.Deliverable;
import dev.hycolony.core.request.model.RequestState;
import dev.hycolony.core.request.model.RequestToken;
import dev.hycolony.core.request.resolver.PlayerResolver;
import dev.hycolony.core.request.resolver.RetryingResolver;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Builds the clipboard's view (MC WindowClipBoard): the requests only a player can serve, nearest requester first, each
 * followed by its children.
 */
final class RequestViews {
    private final ColonyContext ctx;

    RequestViews(ColonyContext ctx) {
        this.ctx = ctx;
    }

    /**
     * MC ClipboardRequestTreeWindowModule.getOpenRequests: with {@code showImportant} off, the asynchronous requests
     * (a hut's own, filed without a citizen) are left out, as MC's code does when its flag is off; HyColony has no
     * minimum stock requests, MC's other hidden kind.
     */
    RequestsView of(Colony c, UUID player, boolean showImportant) {
        // WindowClipBoard: nearest requester to the player first, then by token (no position: token order only).
        Optional<BlockPos> at = ctx.players().position(player);
        List<Request> sorted = new ArrayList<>(openRoots(c.requests()));
        if (!showImportant) {
            sorted.removeIf(r -> r.citizenId() == Request.NO_CITIZEN);
        }
        sorted.sort(Comparator.comparingLong((Request r) -> at.map(p -> c.buildings()
                                .byRequester(r.requester())
                                .map(b -> b.position().distSq(p))
                                .orElse(Long.MAX_VALUE))
                        .orElse(0L))
                .thenComparing(r -> r.token().id()));
        Map<ItemKey, Integer> owned = ctx.ports().playerInventory().contents(player);
        List<RequestsView.RequestRow> rows = new ArrayList<>();
        sorted.forEach(r -> tree(c, r, 0, owned, rows));
        // Kept at the user's request (RequestsView): Fulfill on a root the player holds items for.
        return new RequestsView(
                c.id(),
                rows.stream()
                        .map(row -> row.withFulfillable(row.depth() == 0 && row.playerHas() > 0))
                        .toList(),
                showImportant);
    }

    /** The roots of the open requests the player or retrying resolver holds, once each. */
    private static List<Request> openRoots(RequestManager m) {
        Map<RequestToken, Request> roots = new LinkedHashMap<>();
        for (String resolver : List.of(PlayerResolver.ID, RetryingResolver.ID)) {
            for (Request r : m.assignedTo(resolver)) {
                if (r.state().isBefore(RequestState.COMPLETED)) {
                    root(m, r).ifPresent(root -> roots.putIfAbsent(root.token(), root));
                }
            }
        }
        return new ArrayList<>(roots.values());
    }

    /** The top of {@code r}'s tree; empty if a parent on the way is gone. */
    private static Optional<Request> root(RequestManager m, Request r) {
        Request root = r;
        while (root != null && root.parent().isPresent()) {
            root = m.get(root.parent().get()).orElse(null);
        }
        return Optional.ofNullable(root);
    }

    /**
     * MC RequestTreeWindowModule.constructTreeFromRequest: {@code r} at {@code depth}, then each of its children still
     * known one level deeper, appended to {@code rows} without Fulfill (each window sets its own rule); a request
     * already listed is skipped (a tree walk never loops).
     */
    void tree(Colony c, Request r, int depth, Map<ItemKey, Integer> owned, List<RequestsView.RequestRow> rows) {
        if (rows.stream().anyMatch(row -> row.token().equals(r.token()))) {
            return;
        }
        rows.add(new RequestsView.RequestRow(
                r.token(),
                r.requestable(),
                RequesterLocation.displayName(c, r),
                RequesterLocation.of(c, r.requester()),
                c.requests().resolverOf(r.token()).map(Resolver::displayName),
                has(r, owned),
                depth,
                false));
        for (RequestToken child : r.children()) {
            c.requests().get(child).ifPresent(k -> tree(c, k, depth + 1, owned, rows));
        }
    }

    /** How many of {@code owned} match {@code r}; 0 for a courier delivery or pickup, which a player cannot provide. */
    private int has(Request r, Map<ItemKey, Integer> owned) {
        Deliverable d = r.deliverable().orElse(null);
        if (d == null) {
            return 0;
        }
        int has = 0;
        for (Map.Entry<ItemKey, Integer> e : owned.entrySet()) {
            if (d.matches(e.getKey(), ctx.ports().catalog())) {
                has += e.getValue();
            }
        }
        return has;
    }
}
