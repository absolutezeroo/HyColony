package dev.hycolony.core.colony.view;

import dev.hycolony.core.building.Building;
import dev.hycolony.core.citizen.CitizenData;
import dev.hycolony.core.colony.Colony;
import dev.hycolony.core.colony.ColonyContext;
import dev.hycolony.core.colony.ui.RequestsView;
import dev.hycolony.core.kernel.BlockPos;
import dev.hycolony.core.kernel.item.ItemKey;
import dev.hycolony.core.request.Request;
import dev.hycolony.core.request.RequestManager;
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

/** Builds the clipboard's view (MC WindowClipBoard): the requests only a player can serve, nearest requester first. */
final class RequestViews {
    private final ColonyContext ctx;

    RequestViews(ColonyContext ctx) {
        this.ctx = ctx;
    }

    RequestsView of(Colony c, UUID player) {
        // WindowClipBoard: nearest requester to the player first, then by token (no position: token order only).
        Optional<BlockPos> at = ctx.players().position(player);
        List<Request> sorted = new ArrayList<>(openRoots(c.requests()));
        sorted.sort(Comparator.comparingLong((Request r) -> at.map(p -> c.buildings()
                                .byRequester(r.requester())
                                .map(b -> b.position().distSq(p))
                                .orElse(Long.MAX_VALUE))
                        .orElse(0L))
                .thenComparing(r -> r.token().id()));
        Map<ItemKey, Integer> owned = ctx.ports().playerInventory().contents(player);
        List<RequestsView.RequestRow> rows =
                sorted.stream().map(r -> row(c, r, owned)).toList();
        return new RequestsView(c.id(), rows);
    }

    /** The roots of the open requests the player or retrying resolver holds, once each. */
    private static List<Request> openRoots(RequestManager m) {
        Map<RequestToken, Request> roots = new LinkedHashMap<>();
        for (String resolver : List.of(PlayerResolver.ID, RetryingResolver.ID)) {
            for (Request r : m.assignedTo(resolver)) {
                if (r.state().ordinal() < RequestState.COMPLETED.ordinal()) {
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

    /** A request as the player sees it: who asks, and how many matching items {@code owned} holds. */
    RequestsView.RequestRow row(Colony c, Request r, Map<ItemKey, Integer> owned) {
        int has = 0;
        for (Map.Entry<ItemKey, Integer> e : owned.entrySet()) {
            if (r.requestable().matches(e.getKey(), ctx.ports().catalog())) {
                has += e.getValue();
            }
        }
        String requester = r.citizenId() != -1
                ? c.citizens().get(r.citizenId()).map(CitizenData::name).orElse("")
                : c.buildings()
                        .byRequester(r.requester())
                        .map(Building::displayName)
                        .orElse(r.requester().value());
        return new RequestsView.RequestRow(r.token(), r.requestable(), requester, has);
    }
}
