package dev.hycolony.core.colony.ui;

import dev.hycolony.core.request.RequestToken;
import java.util.List;

/** Open requests the player can supply (the clipboard): those held by the player and retrying resolvers. */
public record RequestsView(int colonyId, List<RequestRow> rows) {
    public record RequestRow(RequestToken token, String description, String requesterName, int playerHas) {}

    public RequestsView {
        rows = List.copyOf(rows);
    }
}
