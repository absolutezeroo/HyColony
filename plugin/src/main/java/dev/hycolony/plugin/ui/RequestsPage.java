package dev.hycolony.plugin.ui;

import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.ui.builder.UICommandBuilder;
import com.hypixel.hytale.server.core.ui.builder.UIEventBuilder;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import dev.hycolony.core.colony.ColonyManager;
import dev.hycolony.core.colony.ui.RequestsView;
import dev.hycolony.core.colony.ui.RequestsView.RequestRow;
import java.util.List;
import javax.annotation.Nonnull;

/** The colony's open requests the player can supply (MineColonies' clipboard). */
public final class RequestsPage extends ColonyPage {
    private final RequestsView view;

    public RequestsPage(PlayerRef playerRef, RequestsView view, ColonyManager manager) {
        super(playerRef, manager);
        this.view = view;
    }

    @Override
    public void build(@Nonnull Ref<EntityStore> ref, @Nonnull UICommandBuilder ui, @Nonnull UIEventBuilder events,
                      @Nonnull Store<EntityStore> store) {
        ui.append("Pages/HyColony/Requests.ui");
        List<RequestRow> rows = view.rows();
        if (rows.isEmpty()) {
            ui.set("#Empty.Visible", true);
            ui.set("#Empty.Text", Message.translation("hycolony.ui.requests.empty"));
        }
        for (int i = 0; i < rows.size(); i++) {
            RequestRow r = rows.get(i);
            String row = "#Requests[" + i + "]";
            ui.append("#Requests", "Pages/HyColony/RequestRow.ui");
            ui.set(row + " #Description.Text", r.description());
            ui.set(row + " #Info.Text", Message.translation("hycolony.ui.requests.info")
                    .param("p0", r.requesterName()).param("p1", String.valueOf(r.playerHas())));
            if (r.playerHas() > 0) {
                bind(events, row + " #FulfilButton", "fulfil", i);
            } else {
                ui.set(row + " #FulfilButton.Visible", false);
            }
        }
    }

    @Override
    public void handleDataEvent(@Nonnull Ref<EntityStore> ref, @Nonnull Store<EntityStore> store, @Nonnull Act act) {
        if (act.action.equals("fulfil") && act.index >= 0 && act.index < view.rows().size()) {
            manager.fulfil(player, view.colonyId(), view.rows().get(act.index).token());
            manager.openRequests(player, view.colonyId()); // fulfil does not re-show
        }
    }
}
