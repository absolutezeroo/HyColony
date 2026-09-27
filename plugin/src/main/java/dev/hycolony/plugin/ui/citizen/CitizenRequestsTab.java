package dev.hycolony.plugin.ui.citizen;

import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.ui.builder.UICommandBuilder;
import com.hypixel.hytale.server.core.ui.builder.UIEventBuilder;
import dev.hycolony.core.colony.ColonyManager;
import dev.hycolony.core.colony.ui.CitizenView;
import dev.hycolony.core.colony.ui.RequestsView.RequestRow;
import dev.hycolony.plugin.ui.ColonyPage;
import dev.hycolony.plugin.ui.RequestsPage;
import java.util.List;
import java.util.UUID;

/** The citizen's Requests tab (MC RequestWindowCitizen): its open requests, "Supply" when the player holds the item. */
final class CitizenRequestsTab {
    private final ColonyManager manager;
    private final UUID player;
    private final CitizenView view;

    CitizenRequestsTab(ColonyManager manager, UUID player, CitizenView view) {
        this.manager = manager;
        this.player = player;
        this.view = view;
    }

    void render(UICommandBuilder ui, UIEventBuilder events) {
        List<RequestRow> rows = view.requests();
        if (rows.isEmpty()) {
            ui.set("#RequestsEmpty.Visible", true);
            ui.set("#RequestsEmpty.Text", Message.translation("hycolony.ui.requests.empty"));
        }
        for (int i = 0; i < rows.size(); i++) {
            RequestsPage.appendRow(ui, events, "#Requests", i, rows.get(i));
        }
    }

    /** Fulfil does not re-show the window: it is shown again here, on the same tab. */
    void handle(ColonyPage.Act act) {
        if ("fulfil".equals(act.action())
                && act.index() >= 0
                && act.index() < view.requests().size()) {
            manager.requestActions()
                    .fulfil(
                            player,
                            view.colonyId(),
                            view.requests().get(act.index()).token());
            manager.windows().openCitizen(player, view.colonyId(), view.citizenId());
        }
    }
}
