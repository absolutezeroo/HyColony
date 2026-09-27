package dev.hycolony.plugin.ui.citizen;

import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.logger.HytaleLogger;
import com.hypixel.hytale.protocol.packets.interface_.Page;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.Universe;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import dev.hycolony.core.citizen.CitizenData;
import dev.hycolony.core.colony.ColonyEvents;
import dev.hycolony.core.colony.ColonyManager;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.function.BooleanSupplier;
import java.util.function.Supplier;
import java.util.logging.Level;

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
     * Opens the container like vanilla {@code /inv see} (a Bench page with one ContainerWindow). The core checked the
     * permission; an offline player or a gone citizen is ignored.
     */
    public void open(UUID player, int colonyId, int citizenId) {
        PlayerRef pr = Universe.get().getPlayer(player);
        Ref<EntityStore> ref = pr == null ? null : pr.getReference();
        CitizenData citizen = manager.get()
                .byId(colonyId)
                .flatMap(c -> c.citizens().get(citizenId))
                .orElse(null);
        if (ref == null || !ref.isValid() || citizen == null) {
            return;
        }
        Store<EntityStore> store = ref.getStore();
        Player playerComponent = store.getComponent(ref, Player.getComponentType());
        if (playerComponent == null) {
            return;
        }
        subscribeOnce();
        // Read on moves only, never per tick.
        BooleanSupplier alive = () -> manager.get()
                .byId(colonyId)
                .flatMap(c -> c.citizens().get(citizenId))
                .isPresent();
        CitizenItemContainer container = new CitizenItemContainer(
                citizen, colonyId, alive, () -> manager.get().citizenInventories());
        CitizenInventoryWindow window = new CitizenInventoryWindow(container, citizen, alive);
        window.coreChanged(); // the client gets the current state on open, no need to send it twice
        if (playerComponent.getPageManager().setPageWithWindows(ref, store, Page.Bench, true, window)) {
            open.add(new Open(ref, colonyId, window));
            window.registerCloseEvent(e -> forget(window));
        }
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
                if (isOpen(o)) {
                    o.window().close(o.player(), o.player().getStore()); // its close event forgets it
                }
            } catch (RuntimeException e) {
                LOG.at(Level.SEVERE).withCause(e).log("HyColony: could not close a citizen inventory window");
            }
            forget(o.window());
        }
    }

    /**
     * Whether the player's window manager still holds this very window: {@code Window.equals} only compares id, type
     * and player, which a later window may share.
     */
    @SuppressWarnings("PMD.CompareObjectsWithEquals")
    private static boolean isOpen(Open o) {
        if (!o.player().isValid()) {
            return false;
        }
        Player playerComponent = o.player().getStore().getComponent(o.player(), Player.getComponentType());
        return playerComponent != null
                && playerComponent.getWindowManager().getWindow(o.window().getId()) == o.window();
    }

    /** Drops the entry of this very window, if still listed. */
    @SuppressWarnings("PMD.CompareObjectsWithEquals") // identity: see isOpen
    private void forget(CitizenInventoryWindow window) {
        open.removeIf(o -> o.window() == window);
    }
}
