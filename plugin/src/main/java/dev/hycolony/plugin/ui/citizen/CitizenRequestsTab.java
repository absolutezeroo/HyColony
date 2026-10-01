package dev.hycolony.plugin.ui.citizen;

import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.server.core.ui.builder.UICommandBuilder;
import com.hypixel.hytale.server.core.ui.builder.UIEventBuilder;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import dev.hycolony.core.app.ColonyManager;
import dev.hycolony.core.app.ui.CitizenView;
import dev.hycolony.plugin.IdMap;
import dev.hycolony.plugin.ui.ColonyPage;
import dev.hycolony.plugin.ui.request.RequestTree;
import dev.hycolony.plugin.ui.request.RequestTreeEvents;

/**
 * The citizen's Requests page (MC RequestWindowCitizen): "Open requests:" over the request tree of the citizen's
 * requests then its workplace's; the core sets where Fulfill and Cancel apply, and each re-shows this window.
 */
final class CitizenRequestsTab {
    private static final String TREE = "#RequestsPage #Tree";

    private final CitizenView view;
    private final IdMap ids;
    private final RequestTreeEvents events;

    CitizenRequestsTab(ColonyManager manager, PlayerRef playerRef, CitizenView view, IdMap ids) {
        this.view = view;
        this.ids = ids;
        this.events = new RequestTreeEvents(
                manager,
                playerRef,
                view.colonyId(),
                view.requests(),
                ids,
                () -> manager.windows().openCitizen(playerRef.getUuid(), view.colonyId(), view.citizenId()));
    }

    void render(UICommandBuilder ui, UIEventBuilder events) {
        RequestTree.render(ui, events, TREE, view.requests(), ids);
    }

    /** Handles a tree event of {@code page}; false for any other. */
    boolean handle(Ref<EntityStore> ref, Store<EntityStore> store, ColonyPage page, ColonyPage.Act act) {
        return events.handle(ref, store, page, act);
    }
}
