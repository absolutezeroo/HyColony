package dev.hycolony.plugin.ui.citizen;

import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.protocol.packets.interface_.Page;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.Universe;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import dev.hycolony.core.citizen.CitizenData;
import dev.hycolony.core.colony.ColonyEvents;
import dev.hycolony.core.colony.ColonyManager;
import dev.hycolony.core.kernel.item.Inventory;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Supplier;

/**
 * The citizen inventory windows open in one world: opens them, and closes them when their citizen is gone. World
 * thread only.
 */
public final class CitizenInventoryWindows {
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
        Supplier<Optional<Inventory>> core = () -> manager.get()
                .byId(colonyId)
                .flatMap(c -> c.citizens().get(citizenId))
                .map(CitizenData::inventory);
        if (ref == null || !ref.isValid() || core.get().isEmpty()) {
            return;
        }
        Store<EntityStore> store = ref.getStore();
        Player playerComponent = store.getComponent(ref, Player.getComponentType());
        if (playerComponent == null) {
            return;
        }
        subscribeOnce();
        CitizenItemContainer container = new CitizenItemContainer(
                CitizenData.INVENTORY_SLOTS,
                core,
                before -> manager.get().citizenInventories().onPlayerEdit(colonyId, citizenId, before));
        CitizenInventoryWindow window = new CitizenInventoryWindow(container, core);
        window.coreChanged(); // the client gets the current state on open, no need to send it twice
        if (playerComponent.getPageManager().setPageWithWindows(ref, store, Page.Bench, true, window)) {
            Open o = new Open(ref, colonyId, window);
            open.add(o);
            window.registerCloseEvent(e -> open.remove(o));
        }
    }

    /** Citizens only leave with their colony (no death yet), so its deletion is when their windows close. */
    private void subscribeOnce() {
        if (!subscribed) {
            subscribed = true;
            manager.get().context().bus().subscribe(ColonyEvents.ColonyDeleted.class, e -> closeAll(e.colonyId()));
        }
    }

    /** Closes the colony's windows still open; backwards, since each close removes its entry. */
    private void closeAll(int colonyId) {
        for (int i = open.size() - 1; i >= 0; i--) {
            Open o = open.get(i);
            if (o.colonyId() != colonyId) {
                continue;
            }
            Player playerComponent = o.player().isValid()
                    ? o.player().getStore().getComponent(o.player(), Player.getComponentType())
                    : null;
            boolean stillOpen = playerComponent != null
                    && o.window()
                            .equals(playerComponent
                                    .getWindowManager()
                                    .getWindow(o.window().getId()));
            if (stillOpen) {
                o.window().close(o.player(), o.player().getStore()); // its close event drops the entry
            } else {
                open.remove(i);
            }
        }
    }
}
