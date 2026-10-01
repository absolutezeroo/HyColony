package dev.hycolony.plugin.ui;

import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.server.core.ui.builder.UICommandBuilder;
import com.hypixel.hytale.server.core.ui.builder.UIEventBuilder;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import dev.hycolony.core.app.ColonyManager;
import dev.hycolony.core.app.ui.RequestsView;
import dev.hycolony.plugin.IdMap;
import dev.hycolony.plugin.ui.clipboard.ClipboardItem;
import dev.hycolony.plugin.ui.request.RequestTree;
import dev.hycolony.plugin.ui.request.RequestTreeEvents;
import javax.annotation.Nonnull;

/**
 * The clipboard's window (MC WindowClipBoard): the requests only a player can serve as a request tree, and the "!"
 * button, whose state the clipboard item keeps (MC ItemSettingMessage).
 */
public final class RequestsPage extends ColonyPage {
    private final RequestsView view;
    private final IdMap ids;
    private final RequestTreeEvents tree;

    public RequestsPage(PlayerRef playerRef, RequestsView view, ColonyManager manager, IdMap ids) {
        super(playerRef, manager);
        this.view = view;
        this.ids = ids;
        this.tree = new RequestTreeEvents(
                manager,
                playerRef,
                view.colonyId(),
                view.rows(),
                ids,
                () -> manager.windows().openRequests(player, view.colonyId(), view.showImportant()));
    }

    public RequestsView view() {
        return view;
    }

    @Override
    public void build(
            @Nonnull Ref<EntityStore> ref,
            @Nonnull UICommandBuilder ui,
            @Nonnull UIEventBuilder events,
            @Nonnull Store<EntityStore> store) {
        ui.append("Pages/HyColony/Requests.ui");
        String important = view.showImportant() ? "#ImportantOn" : "#ImportantOff";
        ui.set(important + ".Visible", true);
        bind(events, important, "important");
        RequestTree.render(ui, events, "#Tree", view.rows(), ids);
    }

    @Override
    public void handleDataEvent(@Nonnull Ref<EntityStore> ref, @Nonnull Store<EntityStore> store, @Nonnull Act act) {
        if (!"important".equals(act.action())) {
            tree.handle(ref, store, this, act);
            return;
        }
        // MC toggleImportant: the flag flips, the item keeps it, the list is drawn again.
        boolean on = !view.showImportant();
        ClipboardItem.lastUsed(player).ifPresent(item -> ClipboardItem.used(player, item.withShowImportant(on)));
        manager.windows().openRequests(player, view.colonyId(), on);
    }
}
