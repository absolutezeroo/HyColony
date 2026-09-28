package dev.hyblockui.api;

import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.logger.HytaleLogger;
import com.hypixel.hytale.server.core.asset.type.item.config.Item;
import com.hypixel.hytale.server.core.inventory.InventoryComponent;
import com.hypixel.hytale.server.core.inventory.ItemStack;
import com.hypixel.hytale.server.core.inventory.container.SimpleItemContainer;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import java.util.logging.Level;

/** Gives items to a player, as Hytale's crafting benches hand out their output. World thread. */
public final class PlayerItems {
    private static final HytaleLogger LOG = HytaleLogger.forEnclosingClass();

    private PlayerItems() {}

    /**
     * Gives count of itemId in stacks of the item's size (hotbar first), dropping at the player's feet what does not
     * fit. A failure is logged at SEVERE and stops giving the rest, never thrown.
     */
    public static void give(Store<EntityStore> store, Ref<EntityStore> player, String itemId, int count) {
        Item item = Item.getAssetMap().getAsset(itemId);
        int stackSize = item == null ? count : Math.max(1, item.getMaxStack());
        for (int left = count; left > 0; left -= stackSize) {
            try {
                SimpleItemContainer.addOrDropItemStack(
                        store,
                        player,
                        InventoryComponent.getCombined(store, player, InventoryComponent.HOTBAR_FIRST),
                        new ItemStack(itemId, Math.min(stackSize, left)));
            } catch (RuntimeException e) {
                LOG.at(Level.SEVERE).withCause(e).log("hyblockui: could not give %s x%d", itemId, left);
                return;
            }
        }
    }
}
