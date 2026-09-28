package dev.hycolony.plugin.inventory;

import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.server.core.inventory.InventoryComponent;
import com.hypixel.hytale.server.core.inventory.ItemStack;
import com.hypixel.hytale.server.core.inventory.container.ItemContainer;
import com.hypixel.hytale.server.core.ui.builder.UICommandBuilder;
import com.hypixel.hytale.server.core.ui.builder.UIEventBuilder;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;

/**
 * Draws the player's own panels into a custom page, as Hytale's inventory shows them: the character panel (name,
 * character, armor, utility slot, stats) and the storage panel (storage and hotbar, with the active slot). Their
 * grids are draggable (InventoryGrids).
 */
public final class PlayerPanels {
    private static final String CHARACTER = "Pages/HyColony/PlayerCharacterPanel.ui";
    private static final String STORAGE = "Pages/HyColony/PlayerStoragePanel.ui";
    private static final String[] ARMOR_ICONS = {
        "#ArmorIconHead", "#ArmorIconChest", "#ArmorIconHands", "#ArmorIconLegs"
    };
    private static final int HOTBAR_SLOTS = 9;

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
        InventoryGrids.drawPlayerGrid(
                ui,
                events,
                host + " #PlayerUtility",
                PlayerSection.UTILITY,
                PlayerSection.UTILITY.container(store, player));
        PlayerStats stats = PlayerStats.of(store, player);
        ui.set(host + " #StatHealth.Text", stats.health());
        ui.set(host + " #StatStamina.Text", stats.stamina());
        ui.set(host + " #StatMana.Text", stats.mana());
        ui.set(host + " #StatDefense.Text", stats.defense());
    }

    /** Appends the storage panel into host and fills it for player, framing their active hotbar slot. */
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
        InventoryComponent.Hotbar hotbar = store.getComponent(player, InventoryComponent.Hotbar.getComponentType());
        int active = hotbar == null ? -1 : hotbar.getActiveSlot();
        for (int i = 0; i < HOTBAR_SLOTS; i++) {
            ui.set(host + " #HotbarActive" + i + ".Visible", i == active);
        }
    }

    private static boolean empty(ItemContainer container, short slot) {
        ItemStack stack = container.getItemStack(slot);
        return stack == null || stack.isEmpty();
    }
}
