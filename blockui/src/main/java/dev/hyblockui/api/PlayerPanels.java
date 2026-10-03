package dev.hyblockui.api;

import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.protocol.packets.interface_.CustomUIEventBindingType;
import com.hypixel.hytale.server.core.inventory.InventoryUtils;
import com.hypixel.hytale.server.core.inventory.ItemStack;
import com.hypixel.hytale.server.core.inventory.container.ItemContainer;
import com.hypixel.hytale.server.core.ui.builder.EventData;
import com.hypixel.hytale.server.core.ui.builder.UICommandBuilder;
import com.hypixel.hytale.server.core.ui.builder.UIEventBuilder;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;

/**
 * Draws the player's own panels into a custom page, as Hytale's inventory shows them: the character panel (name,
 * character and armor) and the storage panel (storage and hotbar). Their
 * grids are draggable (InventoryGrids).
 */
public final class PlayerPanels {
    /** The event data action the storage panel's sort button sends ({@link #enableSort}); pages call {@link #sort}. */
    public static final String SORT_ACTION = "hyblockui.sortStorage";

    private static final String CHARACTER = "Pages/HyBlockUI/PlayerCharacterPanel.ui";
    private static final String STORAGE = "Pages/HyBlockUI/PlayerStoragePanel.ui";
    private static final String[] ARMOR_ICONS = {
        "#ArmorIconHead", "#ArmorIconChest", "#ArmorIconHands", "#ArmorIconLegs"
    };

    private PlayerPanels() {}

    /** Appends the character panel into host and fills it for player. */
    public static void drawCharacter(
            UICommandBuilder ui,
            UIEventBuilder events,
            String host,
            Store<EntityStore> store,
            Ref<EntityStore> player) {
        ui.append(host, CHARACTER);
        PlayerRef ref = store.getComponent(player, PlayerRef.getComponentType());
        ui.set(host + " #PlayerName.Text", ref == null ? "" : ref.getUsername());
        ItemContainer armor = PlayerSection.ARMOR.container(store, player);
        InventoryGrids.drawPlayerGrid(ui, events, host + " #PlayerArmor", PlayerSection.ARMOR, armor);
        // The client shows a slot's silhouette only while it is empty.
        for (short i = 0; i < ARMOR_ICONS.length; i++) {
            ui.set(host + " " + ARMOR_ICONS[i] + ".Visible", i >= armor.getCapacity() || empty(armor, i));
        }
    }

    /** Appends the storage panel into host and fills it for player. */
    public static void drawStorage(
            UICommandBuilder ui,
            UIEventBuilder events,
            String host,
            Store<EntityStore> store,
            Ref<EntityStore> player) {
        ui.append(host, STORAGE);
        InventoryGrids.drawPlayerGrid(
                ui,
                events,
                host + " #PlayerStorage",
                PlayerSection.STORAGE,
                PlayerSection.STORAGE.container(store, player));
        InventoryGrids.drawPlayerGrid(
                ui,
                events,
                host + " #PlayerHotbar",
                PlayerSection.HOTBAR,
                PlayerSection.HOTBAR.container(store, player));
    }

    /** Shows the sort button of the storage panel drawn into host; it sends {@link #SORT_ACTION}. */
    public static void enableSort(UICommandBuilder ui, UIEventBuilder events, String host) {
        ui.set(host + " #SortButton.Visible", true);
        events.addEventBinding(
                CustomUIEventBindingType.Activating, host + " #SortButton", EventData.of("Action", SORT_ACTION), false);
    }

    /**
     * Sorts the player's storage as the client's own sort button does (InventoryUtils.sortStorage); nothing for a
     * player whose inventory is locked ({@link InventoryMoves#mayTouch}).
     */
    public static void sort(Store<EntityStore> store, Ref<EntityStore> player) {
        if (InventoryMoves.mayTouch(player, store)) {
            InventoryUtils.sortStorage(player, store);
        }
    }

    private static boolean empty(ItemContainer container, short slot) {
        ItemStack stack = container.getItemStack(slot);
        return stack == null || stack.isEmpty();
    }
}
