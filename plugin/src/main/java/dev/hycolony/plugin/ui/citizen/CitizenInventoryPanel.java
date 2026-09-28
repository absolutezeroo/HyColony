package dev.hycolony.plugin.ui.citizen;

import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.server.core.ui.builder.UICommandBuilder;
import com.hypixel.hytale.server.core.ui.builder.UIEventBuilder;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import dev.hycolony.plugin.inventory.HeldWindows;
import dev.hycolony.plugin.inventory.InventoryDrop;
import dev.hycolony.plugin.inventory.InventoryGrids;
import dev.hycolony.plugin.inventory.InventoryMoves;
import dev.hycolony.plugin.inventory.InventoryWatch;
import dev.hycolony.plugin.inventory.PageRedraw;
import dev.hycolony.plugin.inventory.PlayerPanels;
import dev.hycolony.plugin.inventory.PlayerSection;
import org.jspecify.annotations.Nullable;

/**
 * The citizen's container shown in the citizen window's Inventory tab (MC ContainerCitizenInventory, which the tab
 * opens straight away): its window, open beside the page, drawn as a draggable grid over the player's own inventory
 * and redrawn whenever either changes. One page owns it at a time; a refreshed page takes it over. World thread.
 */
final class CitizenInventoryPanel {
    /** The citizen's grid, also its name in drop events. */
    static final String GRID = "#CitizenSlots";

    private final CitizenInventoryWindow window;
    private final PageRedraw redraw;
    private Runnable owner = () -> {};
    private @Nullable InventoryWatch watch;
    private boolean closed;

    CitizenInventoryPanel(World world, CitizenInventoryWindow window) {
        this.window = window;
        this.redraw = new PageRedraw(world, () -> owner.run(), () -> true);
        window.onChange(redraw::soon);
        window.registerCloseEvent(e -> {
            closed = true;
            redraw.soon(); // the page leaves the tab
        });
    }

    /** The window, to open with the page. */
    CitizenInventoryWindow window() {
        return window;
    }

    /** Makes redrawOwner the page redrawn on changes, the one that shows this panel now. */
    void attach(Runnable redrawOwner) {
        this.owner = redrawOwner;
    }

    /** Whether the window is still open (the colony can close it, or the client). */
    boolean isOpen() {
        return !closed;
    }

    /** Draws the citizen's grid and, into host, the player's inventory; starts following the player's inventory. */
    void draw(
            UICommandBuilder ui,
            UIEventBuilder events,
            String host,
            Store<EntityStore> store,
            Ref<EntityStore> player) {
        if (watch == null) {
            watch = InventoryWatch.start(store, player, redraw::soon);
        }
        InventoryGrids.drawContainer(ui, events, GRID, window.getItemContainer(), window.getId());
        PlayerPanels.drawStorage(ui, events, host, store, player);
    }

    /** Moves what the player dropped on the citizen's grid or their own; a drop elsewhere does nothing. */
    void drop(Ref<EntityStore> player, Store<EntityStore> store, InventoryDrop drop) {
        if (GRID.equals(drop.grid())) {
            InventoryMoves.apply(player, store, drop, window.getId());
            return;
        }
        PlayerSection.byGrid(drop.grid()).ifPresent(part -> InventoryMoves.apply(player, store, drop, part.id()));
    }

    /** Stops following and closes the window if the player still holds it. Never throws. */
    void close(Ref<EntityStore> player, Store<EntityStore> store) {
        closed = true;
        InventoryWatch current = watch;
        watch = null;
        if (current != null) {
            current.stop();
        }
        HeldWindows.closeIfHeld(player, store, window);
    }
}
