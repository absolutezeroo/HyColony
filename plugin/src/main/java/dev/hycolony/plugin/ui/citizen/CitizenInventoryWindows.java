package dev.hycolony.plugin.ui.citizen;

import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.logger.HytaleLogger;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.Universe;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import dev.hycolony.core.app.ColonyManager;
import dev.hycolony.core.citizen.CitizenData;
import dev.hycolony.core.colony.ColonyEvents;
import dev.hycolony.core.kernel.port.BodyId;
import dev.hycolony.plugin.item.HytaleStacks;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.function.BooleanSupplier;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.logging.Level;

/**
 * The citizen inventory pages open in one world: opens them, and closes them when their citizen's colony is gone.
 * World thread only.
 */
public final class CitizenInventoryWindows {
    private static final HytaleLogger LOG = HytaleLogger.forEnclosingClass();

    private record Open(int colonyId, CitizenInventoryPage page) {}

    private final Supplier<ColonyManager> manager;
    private final Function<BodyId, Optional<Ref<EntityStore>>> bodies;
    private final List<Open> open = new ArrayList<>();
    private boolean subscribed;

    /** {@code bodies}: a citizen body's loaded entity, for the camera that shows it. */
    public CitizenInventoryWindows(
            Supplier<ColonyManager> manager, Function<BodyId, Optional<Ref<EntityStore>>> bodies) {
        this.manager = manager;
        this.bodies = bodies;
    }

    /**
     * MC OpenInventoryMessage: opens the citizen's inventory in our page (MC WindowCitizenInventory), in place of the
     * citizen's window, with a window on its 27 slots and one on its armour beside it
     * (PageManager.openCustomPageWithWindows, as HyDomum's cutter). The core checked the permission; an offline player
     * or a gone citizen is ignored.
     *
     * <p>Deviation from MC: our own page, not the game's container screen, so that the camera can show the citizen:
     * Hytale's container screen imposes its camera (citizen-inventory-window.md § 9).
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
        Player playerComponent = store.getComponent(ref, Player.getComponentType());
        if (playerComponent == null) {
            return;
        }
        subscribeOnce();
        CitizenInventoryPage page = newPage(pr, store.getExternalData().getWorld(), colonyId, citizen);
        CitizenInventoryPage.Setup s = page.setup();
        if (playerComponent.getPageManager().openCustomPageWithWindows(ref, store, page, s.main(), s.armor())) {
            open.add(new Open(colonyId, page));
            s.main().registerCloseEvent(e -> forget(page));
        }
    }

    /** The page on {@code citizen}'s inventory, its two windows valid while the citizen exists. */
    private CitizenInventoryPage newPage(PlayerRef player, World world, int colonyId, CitizenData citizen) {
        // Read on moves only, never per tick.
        BooleanSupplier alive = () -> manager.get()
                .byId(colonyId)
                .flatMap(c -> c.citizens().get(citizen.id()))
                .isPresent();
        CitizenItemContainer.Owner owner = new CitizenItemContainer.Owner(citizen, colonyId, alive);
        CitizenPreviewCamera camera = new CitizenPreviewCamera(
                player,
                world,
                () -> manager.get()
                        .byId(colonyId)
                        .flatMap(c -> c.citizens().bodyOf(citizen.id()))
                        .flatMap(bodies));
        return new CitizenInventoryPage(
                player,
                new CitizenInventoryPage.Setup(
                        world,
                        citizen,
                        window(owner, CitizenInventoryPart.MAIN),
                        window(owner, CitizenInventoryPart.ARMOR),
                        camera));
    }

    /** A window on {@code part} of the owner's inventory; the client gets its state on open. */
    private CitizenInventoryWindow window(CitizenItemContainer.Owner owner, CitizenInventoryPart part) {
        CitizenItemContainer container = new CitizenItemContainer(
                owner,
                part,
                () -> manager.get().citizenInventories(),
                new HytaleStacks(manager.get().context().ports().catalog()::durability));
        CitizenInventoryWindow window = new CitizenInventoryWindow(container, owner, part);
        window.coreChanged(); // the client gets the current state on open, no need to send it twice
        return window;
    }

    /** Citizens only leave with their colony (no death yet), so its deletion is when their pages close. */
    private void subscribeOnce() {
        if (!subscribed) {
            subscribed = true;
            manager.get().context().bus().subscribe(ColonyEvents.ColonyDeleted.class, e -> closeAll(e.colonyId()));
        }
    }

    /**
     * Closes the colony's pages still open; backwards, since each close removes its entry. A failing close is logged
     * and forgotten, the others still close.
     */
    private void closeAll(int colonyId) {
        for (int i = open.size() - 1; i >= 0; i--) {
            Open o = open.get(i);
            if (o.colonyId() != colonyId) {
                continue;
            }
            try {
                o.page().closeFromServer(); // its dismissal closes its windows, whose close event forgets it
            } catch (RuntimeException e) {
                LOG.at(Level.SEVERE).withCause(e).log("HyColony: could not close a citizen inventory page");
            }
            forget(o.page());
        }
    }

    /** Drops the entry of this very page, if still listed. */
    @SuppressWarnings("PMD.CompareObjectsWithEquals") // identity: pages are not values
    private void forget(CitizenInventoryPage page) {
        open.removeIf(o -> o.page() == page);
    }
}
