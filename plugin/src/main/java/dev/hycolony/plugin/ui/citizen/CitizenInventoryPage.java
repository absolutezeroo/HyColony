package dev.hycolony.plugin.ui.citizen;

import com.hypixel.hytale.codec.Codec;
import com.hypixel.hytale.codec.KeyedCodec;
import com.hypixel.hytale.codec.builder.BuilderCodec;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.protocol.packets.interface_.CustomPageLifetime;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.entity.entities.player.pages.InteractiveCustomUIPage;
import com.hypixel.hytale.server.core.ui.builder.UICommandBuilder;
import com.hypixel.hytale.server.core.ui.builder.UIEventBuilder;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import dev.hyblockui.api.HeldWindows;
import dev.hyblockui.api.InventoryDrop;
import dev.hyblockui.api.InventoryGrids;
import dev.hyblockui.api.InventoryMoves;
import dev.hyblockui.api.InventoryWatch;
import dev.hyblockui.api.PageEvents;
import dev.hyblockui.api.PageRedraw;
import dev.hyblockui.api.PlayerPanels;
import dev.hyblockui.api.PlayerSection;
import dev.hycolony.core.app.view.CitizenInventoryView;
import dev.hycolony.core.citizen.CitizenData;
import dev.hycolony.plugin.item.HytaleStacks;
import java.util.Optional;
import java.util.function.Supplier;
import javax.annotation.Nonnull;
import org.jspecify.annotations.Nullable;

/**
 * A citizen's inventory window (MC WindowCitizenInventory over ContainerCitizenInventory): its name, armour, held
 * items and stats, its 27 slots under take all, put all and quick stack, then the player's storage and hotbar, all
 * draggable but the hands. Redrawn when a move, the citizen's AI or its stats change them. World thread.
 *
 * <p>Deviation from MC (asked for): laid out as Hytale's own inventory (CitizenInventory.ui), not as MC's window, and
 * the citizen is not drawn (Hytale draws only the player's character in a page). Its 27 slots have no sort button: the
 * hands hold slots, which a sort would move. A shift-click is Hytale's own (InventoryUtils.smartMoveItem): from the
 * citizen's slots or its armour to the player's inventory, placed as the player's settings say; from the player to the
 * citizen's 27 slots, then its armour once they are full. MC ContainerCitizenInventory.quickMoveStack sends the
 * citizen's slots to the player's from their end, the armour last; an armour piece to the citizen's 27 slots; the
 * player's items to those 27 only.
 */
final class CitizenInventoryPage extends InteractiveCustomUIPage<CitizenInventoryPage.Act> {
    private static final String CITIZEN_GRID = "#CitizenSlots";
    private static final String ARMOR_GRID = "#ArmorSlots";
    private static final String STORAGE = "#Storage";

    /** A button's or a grid's event: its action, and for a drop where the item came from and went (InventoryDrop). */
    static final class Act {
        static final BuilderCodec<Act> CODEC = InventoryDrop.appendTo(
                        BuilderCodec.builder(Act.class, Act::new)
                                .append(new KeyedCodec<>("Action", Codec.STRING), (d, v) -> d.action = v, d -> d.action)
                                .add(),
                        d -> d.drop)
                .build();
        String action = "";
        final InventoryDrop drop = new InventoryDrop();
    }

    /**
     * What the page shows: its world, the citizen, the windows on its two parts, the core's view of its side panel
     * (empty once the citizen is gone) and the stacks of its held items.
     */
    record Setup(
            World world,
            CitizenData citizen,
            CitizenInventoryWindow main,
            CitizenInventoryWindow armor,
            Supplier<Optional<CitizenInventoryView>> panel,
            HytaleStacks stacks) {}

    private final Setup setup;
    private final PageRedraw redraw;
    private final PageCheckTimer checks;
    private @Nullable InventoryWatch watch;
    private long drawnChanges = -1;
    private Optional<CitizenInventoryView> drawnPanel = Optional.empty();

    CitizenInventoryPage(PlayerRef playerRef, Setup setup) {
        super(playerRef, CustomPageLifetime.CanDismiss, Act.CODEC);
        this.setup = setup;
        this.redraw = new PageRedraw(setup.world(), this::redrawIfShown, this::isShown);
        this.checks = new PageCheckTimer(setup.world(), playerRef, this::checkNow, this::stopFollowing);
    }

    @Override
    public void build(
            @Nonnull Ref<EntityStore> ref,
            @Nonnull UICommandBuilder ui,
            @Nonnull UIEventBuilder events,
            @Nonnull Store<EntityStore> store) {
        if (watch == null) {
            start(store, ref);
        }
        drawnChanges = coreChanges();
        drawnPanel = setup.panel().get();
        ui.append("Pages/HyColony/CitizenInventory.ui");
        drawnPanel.ifPresent(
                view -> CitizenSidePanel.draw(ui, view, setup.armor().getItemContainer(), setup.stacks()));
        InventoryGrids.drawContainer(
                ui,
                events,
                CITIZEN_GRID,
                setup.main().getItemContainer(),
                setup.main().getId());
        InventoryGrids.drawContainer(
                ui,
                events,
                ARMOR_GRID,
                setup.armor().getItemContainer(),
                setup.armor().getId());
        PlayerPanels.drawStorage(ui, events, STORAGE, store, ref);
        PlayerPanels.enableSort(ui, events, STORAGE);
        CitizenSlotsActions.bind(events);
    }

    /** Follows the player's inventory and the citizen's, and checks the citizen's own changes. */
    private void start(Store<EntityStore> store, Ref<EntityStore> ref) {
        watch = InventoryWatch.start(
                store,
                ref,
                redraw::soon,
                setup.main().getItemContainer(),
                setup.armor().getItemContainer());
        checks.start();
    }

    /**
     * Redraws the page when the citizen's AI changed what it carries or wears, or its side panel changed (health,
     * defense, hunger, hands). A page no longer shown without a dismissal (a world change, a disconnection) stops
     * following.
     */
    private void checkNow() {
        if (!isShown()) {
            stopFollowing();
            return;
        }
        if (coreChanges() != drawnChanges || !setup.panel().get().equals(drawnPanel)) {
            redraw.soon();
        }
    }

    /** A count that moves whenever the citizen's inventory or armour changes. */
    private long coreChanges() {
        return setup.citizen().inventory().changes()
                + setup.citizen().equipment().armor().changes();
    }

    @Override
    public void handleDataEvent(@Nonnull Ref<EntityStore> ref, @Nonnull Store<EntityStore> store, @Nonnull Act act) {
        PageEvents.guard(getClass(), () -> {
            // The moves change the containers, and the watch redraws once.
            switch (act.action) {
                case InventoryGrids.DROP_ACTION -> drop(ref, store, act.drop);
                case PlayerPanels.SORT_ACTION -> PlayerPanels.sort(store, ref);
                default ->
                    CitizenSlotsActions.apply(
                            act.action, ref, store, setup.main().getId());
            }
        });
    }

    /** Moves what the player dropped on one of the page's grids; a drop elsewhere does nothing. */
    private void drop(Ref<EntityStore> ref, Store<EntityStore> store, InventoryDrop drop) {
        switch (drop.grid()) {
            case CITIZEN_GRID ->
                InventoryMoves.apply(ref, store, drop, setup.main().getId());
            case ARMOR_GRID ->
                InventoryMoves.apply(ref, store, drop, setup.armor().getId());
            default ->
                PlayerSection.byGrid(drop.grid()).ifPresent(part -> InventoryMoves.apply(ref, store, drop, part.id()));
        }
    }

    /**
     * Stops following, then closes the citizen's windows at the world's next task, after the client's own close of
     * them when it dismissed the page (HeldWindows.closeLater). Never throws.
     */
    @Override
    public void onDismiss(@Nonnull Ref<EntityStore> ref, @Nonnull Store<EntityStore> store) {
        stopFollowing();
        HeldWindows.closeLater(ref, setup.main());
        HeldWindows.closeLater(ref, setup.armor());
        super.onDismiss(ref, store);
    }

    /** Stops watching the inventories and checking; once is enough. */
    private void stopFollowing() {
        InventoryWatch current = watch;
        watch = null;
        if (current != null) {
            current.stop();
        }
        checks.cancel();
    }

    Setup setup() {
        return setup;
    }

    /**
     * Closes the page from the server (its citizen's colony is gone), its dismissal cleaning up; a page no longer
     * shown only stops following, so that whatever the player sees now stays open.
     */
    void closeFromServer() {
        if (isShown()) {
            close();
        } else {
            stopFollowing();
        }
    }

    private void redrawIfShown() {
        if (isShown()) {
            rebuild();
        }
    }

    /** Whether the player still looks at this page, in this world. */
    private boolean isShown() {
        Ref<EntityStore> ref = playerRef.getReference();
        if (ref == null
                || !ref.isValid()
                || !setup.world().equals(ref.getStore().getExternalData().getWorld())) {
            return false;
        }
        Player shown = ref.getStore().getComponent(ref, Player.getComponentType());
        return shown != null && equals(shown.getPageManager().getCustomPage());
    }
}
