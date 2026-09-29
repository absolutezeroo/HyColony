package dev.hycolony.plugin.ui.citizen;

import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.logger.HytaleLogger;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.entity.entities.player.pages.PageManager;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.Universe;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import dev.hyblockui.api.HeldWindows;
import dev.hycolony.core.app.ColonyManager;
import dev.hycolony.core.citizen.CitizenData;
import dev.hycolony.core.colony.ColonyEvents;
import dev.hycolony.plugin.item.HytaleStacks;
import dev.hycolony.plugin.ui.ColonyPage;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.function.BooleanSupplier;
import java.util.function.Supplier;
import java.util.logging.Level;
import org.jspecify.annotations.Nullable;

/**
 * The citizen inventory windows open in one world: opens them, and closes them when their citizen is gone. World
 * thread only.
 */
public final class CitizenInventoryWindows {
    private static final HytaleLogger LOG = HytaleLogger.forEnclosingClass();

    private record Open(Ref<EntityStore> player, int colonyId, CitizenInventoryWindow window) {}

    private final Supplier<ColonyManager> manager;
    private final List<Open> open = new ArrayList<>();
    private boolean subscribed;

    public CitizenInventoryWindows(Supplier<ColonyManager> manager) {
        this.manager = manager;
    }

    /**
     * Shows the container in the citizen's window, on its Inventory tab: the window is reopened with the container
     * beside it (PageManager.openCustomPageWithWindows). The core checked the permission; an offline player, a gone
     * citizen, or a player not looking at that citizen's window is ignored.
     */
    public void open(UUID player, int colonyId, int citizenId) {
        PlayerRef pr = Universe.get().getPlayer(player);
        Ref<EntityStore> ref = pr == null ? null : pr.getReference();
        CitizenData citizen = manager.get()
                .byId(colonyId)
                .flatMap(c -> c.citizens().get(citizenId))
                .orElse(null);
        if (pr == null || ref == null || !ref.isValid() || citizen == null) {
            return;
        }
        Store<EntityStore> store = ref.getStore();
        Shown shown = shownWindow(store, ref, colonyId, citizenId);
        if (shown == null) {
            return;
        }
        subscribeOnce();
        CitizenInventoryWindow window = newWindow(colonyId, citizenId, citizen);
        CitizenInventoryPanel panel =
                new CitizenInventoryPanel(store.getExternalData().getWorld(), window);
        if (shown.pages().openCustomPageWithWindows(ref, store, shown.page().withInventory(pr, panel), window)) {
            open.add(new Open(ref, colonyId, window));
            window.registerCloseEvent(e -> forget(window));
        }
    }

    /** The player's pages and the citizen window they look at. */
    private record Shown(PageManager pages, CitizenPage page) {}

    /** The citizen window the player looks at, if it is that citizen's; null otherwise. */
    private static @Nullable Shown shownWindow(
            Store<EntityStore> store, Ref<EntityStore> ref, int colonyId, int citizenId) {
        Player playerComponent = store.getComponent(ref, Player.getComponentType());
        if (playerComponent == null) {
            return null;
        }
        PageManager pages = playerComponent.getPageManager();
        return pages.getCustomPage() instanceof ColonyPage colonyPage
                        && colonyPage.live() instanceof CitizenPage page
                        && page.shows(colonyId, citizenId)
                ? new Shown(pages, page)
                : null;
    }

    /** A window on the citizen's inventory, valid while the citizen exists; the client gets its state on open. */
    private CitizenInventoryWindow newWindow(int colonyId, int citizenId, CitizenData citizen) {
        // Read on moves only, never per tick.
        BooleanSupplier alive = () -> manager.get()
                .byId(colonyId)
                .flatMap(c -> c.citizens().get(citizenId))
                .isPresent();
        CitizenItemContainer container = new CitizenItemContainer(
                citizen,
                colonyId,
                alive,
                () -> manager.get().citizenInventories(),
                new HytaleStacks(manager.get().context().ports().catalog()::durability));
        CitizenInventoryWindow window = new CitizenInventoryWindow(container, citizen, alive);
        window.coreChanged(); // the client gets the current state on open, no need to send it twice
        return window;
    }

    /** Citizens only leave with their colony (no death yet), so its deletion is when their windows close. */
    private void subscribeOnce() {
        if (!subscribed) {
            subscribed = true;
            manager.get().context().bus().subscribe(ColonyEvents.ColonyDeleted.class, e -> closeAll(e.colonyId()));
        }
    }

    /**
     * Closes the colony's windows still open; backwards, since each close removes its entry. A failing close is logged
     * and forgotten, the others still close.
     */
    private void closeAll(int colonyId) {
        for (int i = open.size() - 1; i >= 0; i--) {
            Open o = open.get(i);
            if (o.colonyId() != colonyId) {
                continue;
            }
            try {
                if (HeldWindows.holds(o.player(), o.player().getStore(), o.window())) {
                    o.window().close(o.player(), o.player().getStore()); // its close event forgets it
                }
            } catch (RuntimeException e) {
                LOG.at(Level.SEVERE).withCause(e).log("HyColony: could not close a citizen inventory window");
            }
            forget(o.window());
        }
    }

    /** Drops the entry of this very window, if still listed. */
    @SuppressWarnings("PMD.CompareObjectsWithEquals") // identity: a later window may share id, type and player
    private void forget(CitizenInventoryWindow window) {
        open.removeIf(o -> o.window() == window);
    }
}
