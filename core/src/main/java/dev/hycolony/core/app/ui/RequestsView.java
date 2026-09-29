package dev.hycolony.core.app.ui;

import dev.hycolony.core.request.model.RequestToken;
import dev.hycolony.core.request.model.Requestable;
import java.util.List;

/**
 * Open requests the player can supply (the clipboard): those held by the player and retrying resolvers.
 *
 * <p>Deviation from MC: {@code playerHas} lets the clipboard offer "Supply" on each row; MC's clipboard has no such
 * button (ClipboardRequestTreeWindowModule keeps isFulfillable false), it is only in the citizen's Requests tab and the
 * request detail window. Kept at the user's request: there is no clipboard item or detail window yet.
 */
public record RequestsView(int colonyId, List<RequestRow> rows) {
    /**
     * {@code requestable} is the request itself (a stack, a tool, a courier delivery or pickup), for the UI to name in
     * the player's language. {@code depth} is its level in the request tree (MC RequestTreeWindowModule): 0 for a
     * root, one more per child level. {@code playerHas} is 0 for what is not items.
     */
    public record RequestRow(
            RequestToken token, Requestable requestable, String requesterName, int playerHas, int depth) {
        /** Supply is offered on a root the player holds items for; MC only acts on the tree's roots. */
        public boolean canSupply() {
            return depth == 0 && playerHas > 0;
        }
    }

    public RequestsView {
        rows = List.copyOf(rows);
    }
}
