package dev.hycolony.plugin.ui.citizen;

import com.hypixel.hytale.codec.Codec;
import com.hypixel.hytale.codec.KeyedCodec;
import com.hypixel.hytale.codec.builder.BuilderCodec;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.protocol.packets.interface_.CustomPageLifetime;
import com.hypixel.hytale.server.core.HytaleServer;
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
import dev.hyblockui.api.PlayerSection;
import dev.hycolony.core.citizen.CitizenData;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import javax.annotation.Nonnull;
import org.jspecify.annotations.Nullable;

/**
 * A citizen's inventory window (MC WindowCitizenInventory over ContainerCitizenInventory), MC's layout doubled: its
 * name, its 27 slots and 4 armour slots, the frame where the server camera shows it ({@link CitizenPreviewCamera}),
 * and the player's storage and hotbar, all draggable. Redrawn when a move or the citizen's AI changes them. World
 * thread.
 */
final class CitizenInventoryPage extends InteractiveCustomUIPage<CitizenInventoryPage.Act> {
    private static final String CITIZEN_GRID = "#CitizenSlots";
    private static final String ARMOR_GRID = "#ArmorSlots";
    /** How often the camera is checked and the citizen's own changes looked for, in milliseconds. */
    private static final long CHECK_MILLIS = 500;

    /** A grid's drop: its action and where the item came from and went (InventoryDrop). */
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

    /** What the page shows: its world, the citizen, the windows on its two parts and the camera on it. */
    record Setup(
            World world,
            CitizenData citizen,
            CitizenInventoryWindow main,
            CitizenInventoryWindow armor,
            CitizenPreviewCamera camera) {}

    private final Setup setup;
    private final PageRedraw redraw;
    private @Nullable InventoryWatch watch;
    private @Nullable ScheduledFuture<?> check;
    private long drawnChanges = -1;

    CitizenInventoryPage(PlayerRef playerRef, Setup setup) {
        super(playerRef, CustomPageLifetime.CanDismiss, Act.CODEC);
        this.setup = setup;
        this.redraw = new PageRedraw(setup.world(), this::redrawIfShown, this::isShown);
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
        ui.append("Pages/HyColony/CitizenInventory.ui");
        ui.set("#Name.Text", setup.citizen().name());
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
        InventoryGrids.drawPlayerPart(ui, events, "#PlayerStorage", PlayerSection.STORAGE, ref);
        InventoryGrids.drawPlayerPart(ui, events, "#PlayerHotbar", PlayerSection.HOTBAR, ref);
    }

    /** Follows the player's inventory and the citizen's, and puts the camera on the citizen. */
    private void start(Store<EntityStore> store, Ref<EntityStore> ref) {
        watch = InventoryWatch.start(
                store,
                ref,
                redraw::soon,
                setup.main().getItemContainer(),
                setup.armor().getItemContainer());
        setup.camera().follow();
        check = HytaleServer.SCHEDULED_EXECUTOR.scheduleAtFixedRate(
                () -> setup.world().execute(this::checkNow), CHECK_MILLIS, CHECK_MILLIS, TimeUnit.MILLISECONDS);
    }

    /** The camera follows the citizen; the page is redrawn when its AI changed what it carries or wears. */
    private void checkNow() {
        if (!isShown()) {
            return;
        }
        setup.camera().follow();
        if (coreChanges() != drawnChanges) {
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
            if (InventoryGrids.DROP_ACTION.equals(act.action)) {
                drop(ref, store, act.drop); // the move changes the containers, and the watch redraws once
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

    /** Stops following, gives the camera back and closes the citizen's windows. Never throws. */
    @Override
    public void onDismiss(@Nonnull Ref<EntityStore> ref, @Nonnull Store<EntityStore> store) {
        InventoryWatch current = watch;
        watch = null;
        if (current != null) {
            current.stop();
        }
        ScheduledFuture<?> timer = check;
        check = null;
        if (timer != null) {
            timer.cancel(false);
        }
        setup.camera().stop();
        HeldWindows.closeIfHeld(ref, store, setup.main());
        HeldWindows.closeIfHeld(ref, store, setup.armor());
        super.onDismiss(ref, store);
    }

    Setup setup() {
        return setup;
    }

    /** Closes the page from the server (its citizen's colony is gone); its dismissal cleans up. */
    void closeFromServer() {
        close();
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
