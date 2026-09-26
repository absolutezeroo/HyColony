package dev.hycolony.core.colony.ui;

import dev.hycolony.core.request.Deliverable;
import dev.hycolony.core.request.RequestToken;
import java.util.List;

/** Open requests the player can supply (the clipboard): those held by the player and retrying resolvers. */
public record RequestsView(int colonyId, List<RequestRow> rows) {
    /** {@code requestable} is the request itself (a stack or a tool), for the UI to name in the player's language. */
    public record RequestRow(RequestToken token, Deliverable requestable, String requesterName, int playerHas) {}

    public RequestsView {
        rows = List.copyOf(rows);
    }
}
