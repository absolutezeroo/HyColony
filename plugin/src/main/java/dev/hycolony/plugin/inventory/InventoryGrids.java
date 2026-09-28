package dev.hycolony.plugin.inventory;

import com.hypixel.hytale.component.ComponentType;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.protocol.packets.interface_.CustomUIEventBindingType;
import com.hypixel.hytale.server.core.inventory.InventoryComponent;
import com.hypixel.hytale.server.core.inventory.ItemStack;
import com.hypixel.hytale.server.core.inventory.container.ItemContainer;
import com.hypixel.hytale.server.core.ui.builder.EventData;
import com.hypixel.hytale.server.core.ui.builder.UICommandBuilder;
import com.hypixel.hytale.server.core.ui.builder.UIEventBuilder;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import java.util.OptionalInt;

/**
 * Draws item grids in a custom page that the client lets the player drag from and drop on, as in its own inventory
 * (verified in game on 0.6.8): an ItemGrid filled through ItemStacks (not Slots) and given its InventorySectionId.
 * The client then drags natively, shift-clicks natively (SmartMoveItemStack), and reports a drop as a Dropped event,
 * which InventoryMoves carries out.
 */
public final class InventoryGrids {
    /** The player's storage grid, as named in drop events. */
    public static final String STORAGE = "storage";

    /** The player's hotbar grid, as named in drop events. */
    public static final String HOTBAR = "hotbar";

    /** The event data action that every drop sends; pages route it to InventoryMoves. */
    public static final String DROP_ACTION = "inventoryDrop";

    private static final int STORAGE_SECTION = InventoryComponent.STORAGE_SECTION_ID;
    private static final int HOTBAR_SECTION = InventoryComponent.HOTBAR_SECTION_ID;
    private static final String PANEL = "Pages/HyColony/InventoryPanel.ui";

    private InventoryGrids() {}

    /**
     * Appends the player panel (their character, storage and hotbar) into host, fills it from the player's inventory
     * and sends the grids' drops as action events.
     */
    public static void drawPlayer(
            UICommandBuilder ui, UIEventBuilder events, String host, Store<EntityStore> store, Ref<EntityStore> ref) {
        ui.append(host, PANEL);
        String storage = host + " #PlayerStorage";
        fill(ui, storage, playerStacks(store, ref, STORAGE_SECTION), STORAGE_SECTION);
        bindDrop(events, storage, STORAGE);
        String hotbar = host + " #PlayerHotbar";
        fill(ui, hotbar, playerStacks(store, ref, HOTBAR_SECTION), HOTBAR_SECTION);
        bindDrop(events, hotbar, HOTBAR);
    }

    /**
     * Fills the grid at selector with container, open as section (a window id); its drops are sent with the selector
     * as their grid name.
     */
    public static void drawContainer(
            UICommandBuilder ui, UIEventBuilder events, String selector, ItemContainer container, int section) {
        fill(ui, selector, stacks(container), section);
        bindDrop(events, selector, selector);
    }

    /** The player section a grid drawn by drawPlayer stands for; empty for any other grid. */
    public static OptionalInt playerSection(String grid) {
        return switch (grid) {
            case STORAGE -> OptionalInt.of(STORAGE_SECTION);
            case HOTBAR -> OptionalInt.of(HOTBAR_SECTION);
            default -> OptionalInt.empty();
        };
    }

    /** Every slot of the player's inventory part section; none when the player has no such part. */
    private static ItemStack[] playerStacks(Store<EntityStore> store, Ref<EntityStore> ref, int section) {
        ComponentType<EntityStore, ? extends InventoryComponent> type =
                InventoryComponent.getComponentTypeById(section);
        InventoryComponent component = type == null ? null : store.getComponent(ref, type);
        return component == null ? new ItemStack[0] : stacks(component.getInventory());
    }

    private static void fill(UICommandBuilder ui, String selector, ItemStack[] stacks, int section) {
        ui.set(selector + ".InventorySectionId", section);
        ui.set(selector + ".ItemStacks", stacks);
    }

    /** Binds Dropped (the only drag event the client applies to an ItemGrid that this needs). */
    private static void bindDrop(UIEventBuilder events, String selector, String grid) {
        events.addEventBinding(
                CustomUIEventBindingType.Dropped,
                selector,
                EventData.of("Action", DROP_ACTION).append(InventoryDrop.GRID_KEY, grid),
                false);
    }

    /** Every slot of container, an empty stack where it holds nothing (ItemStacks takes no null). */
    private static ItemStack[] stacks(ItemContainer container) {
        ItemStack[] stacks = new ItemStack[container.getCapacity()];
        for (short i = 0; i < stacks.length; i++) {
            ItemStack stack = container.getItemStack(i);
            stacks[i] = stack == null ? ItemStack.EMPTY : stack;
        }
        return stacks;
    }
}
