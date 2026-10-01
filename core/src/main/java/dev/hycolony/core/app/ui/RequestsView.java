package dev.hycolony.core.app.ui;

import dev.hycolony.core.kernel.BlockPos;
import dev.hycolony.core.request.model.RequestToken;
import dev.hycolony.core.request.model.Requestable;
import java.util.List;
import java.util.Optional;

/**
 * Open requests the player can supply (the clipboard): those held by the player and retrying resolvers.
 *
 * <p>Deviation from MC: the clipboard offers Fulfill on a root the player holds items for; MC's clipboard has no such
 * button (ClipboardRequestTreeWindowModule keeps isFulfillable false), it is only in the citizen's Requests tab and the
 * request detail window. Kept at the user's request.
 */
public record RequestsView(int colonyId, List<RequestRow> rows) {
    /**
     * {@code requestable} is the request itself (a stack, a tool, a courier delivery or pickup), for the UI to name in
     * the player's language. {@code requesterPos} is where its requester stands and {@code resolver} who resolves it
     * (MC WindowRequestDetail: a building's type id or custom name, or "Player"). {@code depth} is its level in the
     * request tree (MC RequestTreeWindowModule): 0 for a root, one more per child level. {@code playerHas} is 0 for
     * what is not items. {@code fulfillable}: the window offers Fulfill on it (its rule depends on the window).
     */
    public record RequestRow(
            RequestToken token,
            Requestable requestable,
            String requesterName,
            Optional<BlockPos> requesterPos,
            Optional<String> resolver,
            int playerHas,
            int depth,
            boolean fulfillable) {
        /** MC RequestTreeWindowModule.isCancellable: Cancel is offered on the tree's roots. */
        public boolean cancellable() {
            return depth == 0;
        }

        /** This row with Fulfill offered or not. */
        public RequestRow withFulfillable(boolean offered) {
            return new RequestRow(token, requestable, requesterName, requesterPos, resolver, playerHas, depth, offered);
        }
    }

    public RequestsView {
        rows = List.copyOf(rows);
    }
}
