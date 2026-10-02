package dev.hyblockui.api;

import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.protocol.packets.interface_.CustomUIEventBindingType;
import com.hypixel.hytale.server.core.inventory.ItemStack;
import com.hypixel.hytale.server.core.inventory.container.ItemContainer;
import com.hypixel.hytale.server.core.ui.builder.EventData;
import com.hypixel.hytale.server.core.ui.builder.UICommandBuilder;
import com.hypixel.hytale.server.core.ui.builder.UIEventBuilder;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;

/**
 * Draws item grids in a custom page that the client lets the player drag from and drop on, as in its own inventory
 * (verified in game on 0.6.8): an ItemGrid filled through ItemStacks (not Slots) and given its InventorySectionId.
 * The client then drags natively, shift-clicks natively (SmartMoveItemStack), and reports a drop as a Dropped event,
 * which InventoryMoves carries out.
 */
public final class InventoryGrids {
    /** The event data action that every drop sends; pages route it to InventoryMoves. */
    public static final String DROP_ACTION = "inventoryDrop";

    private InventoryGrids() {}

    /**
     * Fills the grid at selector with container, open as section (a window id); its drops are sent with the selector
     * as their grid name.
     */
    public static void drawContainer(
            UICommandBuilder ui, UIEventBuilder events, String selector, ItemContainer container, int section) {
        fill(ui, selector, container, section);
        bindDrop(events, selector, selector);
    }

    /**
     * Fills a page's own grid at selector with {@code part} of player's inventory, for a page laid out its own way
     * (PlayerPanels draws Hytale's); its drops come back with the part's grid name ({@link PlayerSection#byGrid}).
     */
    public static void drawPlayerPart(
            UICommandBuilder ui, UIEventBuilder events, String selector, PlayerSection part, Ref<EntityStore> player) {
        drawPlayerGrid(ui, events, selector, part, part.container(player.getStore(), player));
    }

    /** Fills the grid at selector with part of the player's inventory, held in container. */
    static void drawPlayerGrid(
            UICommandBuilder ui, UIEventBuilder events, String selector, PlayerSection part, ItemContainer container) {
        fill(ui, selector, container, part.id());
        bindDrop(events, selector, part.grid());
    }

    private static void fill(UICommandBuilder ui, String selector, ItemContainer container, int section) {
        ui.set(selector + ".InventorySectionId", section);
        ui.set(selector + ".ItemStacks", stacks(container));
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
