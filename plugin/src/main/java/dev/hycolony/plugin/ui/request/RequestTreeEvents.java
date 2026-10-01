package dev.hycolony.plugin.ui.request;

import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import dev.hycolony.core.app.ColonyManager;
import dev.hycolony.core.app.ui.RequestsView.RequestRow;
import dev.hycolony.plugin.IdMap;
import dev.hycolony.plugin.ui.ColonyPage;
import java.util.List;

/**
 * A request tree's buttons in a window (MC RequestTreeWindowModule): Fulfill and Cancel go to the core, which checks
 * the right, then the window shows again (MC refreshes its list); the detail button opens the request's window,
 * whose Back shows this one again.
 *
 * @param reopen shows the window the tree is in again, through the core
 */
public record RequestTreeEvents(
        ColonyManager manager, PlayerRef playerRef, int colonyId, List<RequestRow> rows, IdMap ids, Runnable reopen) {
    public RequestTreeEvents {
        rows = List.copyOf(rows);
    }

    /** Handles a tree event of {@code page}; false for any other event. A request gone meanwhile does nothing. */
    public boolean handle(Ref<EntityStore> ref, Store<EntityStore> store, ColonyPage page, ColonyPage.Act act) {
        switch (act.action()) {
            case RequestTree.FULFILL -> {
                // MC checks isFulfillable again before acting.
                RequestTree.row(act, rows).filter(RequestRow::fulfillable).ifPresent(r -> fulfil(r));
                reopen.run();
            }
            case RequestTree.CANCEL -> {
                RequestTree.row(act, rows).ifPresent(r -> cancel(r));
                reopen.run();
            }
            case RequestTree.DETAIL ->
                RequestTree.row(act, rows)
                        .ifPresent(r -> ColonyPage.open(ref, store, new RequestDetailPage(this, r, page)));
            default -> {
                return false;
            }
        }
        return true;
    }

    void fulfil(RequestRow r) {
        manager.requestActions().fulfil(playerRef.getUuid(), colonyId, r.token());
    }

    void cancel(RequestRow r) {
        manager.requestActions().cancel(playerRef.getUuid(), colonyId, r.token());
    }
}
