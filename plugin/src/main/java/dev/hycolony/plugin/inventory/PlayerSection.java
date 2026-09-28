package dev.hycolony.plugin.inventory;

import com.hypixel.hytale.component.ComponentType;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.server.core.inventory.InventoryComponent;
import com.hypixel.hytale.server.core.inventory.container.EmptyItemContainer;
import com.hypixel.hytale.server.core.inventory.container.ItemContainer;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import java.util.Arrays;
import java.util.Optional;

/** The parts of the player's inventory a page shows as grids: their section id and their grid name in drop events. */
public enum PlayerSection {
    STORAGE(InventoryComponent.STORAGE_SECTION_ID, "storage"),
    HOTBAR(InventoryComponent.HOTBAR_SECTION_ID, "hotbar"),
    ARMOR(InventoryComponent.ARMOR_SECTION_ID, "armor"),
    UTILITY(InventoryComponent.UTILITY_SECTION_ID, "utility");

    private final int id;
    private final String grid;

    PlayerSection(int id, String grid) {
        this.id = id;
        this.grid = grid;
    }

    /** The section id Hytale's inventory packets and InventoryUtils use. */
    public int id() {
        return id;
    }

    /** The grid name sent in this part's drop events. */
    String grid() {
        return grid;
    }

    /** The part a drop event's grid name stands for; empty for any other grid. */
    public static Optional<PlayerSection> byGrid(String grid) {
        return Arrays.stream(values()).filter(s -> s.grid.equals(grid)).findFirst();
    }

    /** This part of player's inventory; an empty container when the player has none. */
    ItemContainer container(Store<EntityStore> store, Ref<EntityStore> player) {
        ComponentType<EntityStore, ? extends InventoryComponent> type = InventoryComponent.getComponentTypeById(id);
        InventoryComponent component = type == null ? null : store.getComponent(player, type);
        return component == null ? EmptyItemContainer.INSTANCE : component.getInventory();
    }
}
