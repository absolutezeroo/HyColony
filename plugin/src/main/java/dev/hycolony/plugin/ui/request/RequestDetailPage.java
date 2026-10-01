package dev.hycolony.plugin.ui.request;

import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.logger.HytaleLogger;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.ui.builder.UICommandBuilder;
import com.hypixel.hytale.server.core.ui.builder.UIEventBuilder;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import dev.hycolony.core.app.ui.RequestsView.RequestRow;
import dev.hycolony.plugin.ui.ColonyPage;
import java.util.logging.Level;
import javax.annotation.Nonnull;

/**
 * A request's details (MC WindowRequestDetail): its item, requester, the requester's place, its resolver and its long
 * description, then Back, Fulfill and Cancel, the last two enabled as the tree offers them. Fulfill and Cancel go to
 * the core and, as Back and Escape (MC closes back to the parent window), show the window the request came from again.
 *
 * <p>Deviation from MC: Back works (MC's window registers no handler for it, only Escape returns); the place is "x, y,
 * z" without MC's dimension.
 */
public final class RequestDetailPage extends ColonyPage {
    private static final HytaleLogger LOG = HytaleLogger.forEnclosingClass();

    private final RequestTreeEvents tree;
    private final RequestRow row;
    private final ColonyPage origin;
    /** Set once this window hands over to its origin, so its dismissal does not show the origin twice. */
    private boolean leaving;

    RequestDetailPage(RequestTreeEvents tree, RequestRow row, ColonyPage origin) {
        super(tree.playerRef(), tree.manager());
        this.tree = tree;
        this.row = row;
        this.origin = origin;
    }

    /** The window this one was opened from, which a re-show of it takes its state from. */
    public ColonyPage origin() {
        return origin;
    }

    @Override
    public void build(
            @Nonnull Ref<EntityStore> ref,
            @Nonnull UICommandBuilder ui,
            @Nonnull UIEventBuilder events,
            @Nonnull Store<EntityStore> store) {
        ui.append("Pages/HyColony/RequestDetail.ui");
        RequestTree.icon(ui, "", row, tree.ids());
        RequestTree.text(ui, "", row.requestable(), RequestTexts.describeLong(row.requestable()));
        ui.set("#Requester.TextSpans", buildingName(row.requesterName()));
        ui.set("#Place.TextSpans", RequestTree.place(row));
        row.resolver()
                .ifPresent(name -> ui.set(
                        "#Resolver.TextSpans",
                        Message.translation("hycolony.ui.requests.resolver").param("p0", buildingName(name))));
        bind(events, "#Back", "back");
        if (row.fulfillable()) {
            bind(events, "#Fulfill", "fulfill");
        } else {
            ui.set("#Fulfill.Disabled", true);
        }
        if (row.cancellable()) {
            bind(events, "#Cancel", "cancel");
        } else {
            ui.set("#Cancel.Disabled", true);
        }
    }

    @Override
    public void handleDataEvent(@Nonnull Ref<EntityStore> ref, @Nonnull Store<EntityStore> store, @Nonnull Act act) {
        switch (act.action()) {
            case "fulfill" -> {
                if (row.fulfillable()) { // MC tests the request's fulfillability; the core checks the items again
                    tree.fulfil(row);
                }
            }
            case "cancel" -> tree.cancel(row);
            case "back" -> {}
            default -> {
                return;
            }
        }
        leaving = true;
        tree.reopen().run();
        closeIfStillShown(ref, store);
    }

    /**
     * Escape: as MC's close, the origin shows again on its tab once this window is gone (world thread, next tick),
     * unless another HyColony or custom window opened meanwhile; a native page (a container) is not seen, so the origin
     * would replace it. Nothing when the world no longer takes tasks.
     */
    @Override
    public void onDismiss(@Nonnull Ref<EntityStore> ref, @Nonnull Store<EntityStore> store) {
        super.onDismiss(ref, store);
        if (leaving) {
            return;
        }
        leaving = true;
        try {
            store.getExternalData().getWorld().execute(() -> {
                Player p = ref.isValid() ? store.getComponent(ref, Player.getComponentType()) : null;
                if (p != null && p.getPageManager().getCustomPage() == null) {
                    reshowFor(this, tree.reopen());
                }
            });
        } catch (RuntimeException e) {
            LOG.at(Level.FINE).withCause(e).log("HyColony request detail: origin not shown again");
        }
    }

    /** The origin could not show again (its citizen or colony is gone): nothing replaced this window, so it closes. */
    // Identity: the open page must be this very page.
    @SuppressWarnings({"PMD.CompareObjectsWithEquals", "ReferenceEquality"})
    private void closeIfStillShown(Ref<EntityStore> ref, Store<EntityStore> store) {
        Player p = store.getComponent(ref, Player.getComponentType());
        if (p != null && p.getPageManager().getCustomPage() == this) {
            close();
        }
    }
}
