package dev.hycolony.core.colony.ui;

import dev.hycolony.core.request.model.Deliverable;
import dev.hycolony.core.request.model.RequestToken;
import java.util.List;

/**
 * Open requests the player can supply (the clipboard): those held by the player and retrying resolvers.
 *
 * <p>Deviation from MC: {@code playerHas} lets the clipboard offer "Supply" on each row; MC's clipboard has no such
 * button (ClipboardRequestTreeWindowModule keeps isFulfillable false), it is only in the citizen's Requests tab and the
 * request detail window. Kept at the user's request: there is no clipboard item or detail window yet.
 */
public record RequestsView(int colonyId, List<RequestRow> rows) {
    /** {@code requestable} is the request itself (a stack or a tool), for the UI to name in the player's language. */
    public record RequestRow(RequestToken token, Deliverable requestable, String requesterName, int playerHas) {}

    public RequestsView {
        rows = List.copyOf(rows);
    }
}
