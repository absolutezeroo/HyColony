package dev.hycolony.plugin.ornament.cutter;

import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.logger.HytaleLogger;
import com.hypixel.hytale.server.core.asset.type.item.config.Item;
import com.hypixel.hytale.server.core.inventory.InventoryComponent;
import com.hypixel.hytale.server.core.inventory.ItemStack;
import com.hypixel.hytale.server.core.inventory.container.ItemContainer;
import com.hypixel.hytale.server.core.inventory.container.SimpleItemContainer;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Level;

/**
 * The player's inventory as the cutter uses it, as Hytale's crafting benches do: what they hold, taking materials
 * from it and giving crafted items to it (dropped at their feet when full). World thread.
 */
final class CutterInventory {
    private static final HytaleLogger LOG = HytaleLogger.forEnclosingClass();

    private CutterInventory() {}

    /** Item id -> how many the player holds, hotbar and storage together. */
    static Map<String, Integer> counts(Store<EntityStore> store, Ref<EntityStore> player) {
        Map<String, Integer> counts = new HashMap<>();
        combined(store, player).forEach((slot, stack) -> {
            if (stack != null && !stack.isEmpty()) {
                counts.merge(stack.getItemId(), stack.getQuantity(), Integer::sum);
            }
        });
        return counts;
    }

    /**
     * Takes every item of materials (item id -> count), all or nothing: when one cannot be taken whole, what was
     * already taken is given back and false returned.
     */
    static boolean take(Store<EntityStore> store, Ref<EntityStore> player, Map<String, Integer> materials) {
        ItemContainer inventory = combined(store, player);
        List<ItemStack> taken = new ArrayList<>();
        for (Map.Entry<String, Integer> material : materials.entrySet()) {
            ItemStack wanted = new ItemStack(material.getKey(), material.getValue());
            if (!inventory.removeItemStack(wanted, true, true).succeeded()) {
                taken.forEach(stack -> give(store, player, stack.getItemId(), stack.getQuantity()));
                return false;
            }
            taken.add(wanted);
        }
        return true;
    }

    /** Gives count of itemId, in stacks of the item's size, dropping at the player's feet what does not fit. */
    static void give(Store<EntityStore> store, Ref<EntityStore> player, String itemId, int count) {
        Item item = Item.getAssetMap().getAsset(itemId);
        int stackSize = item == null ? count : Math.max(1, item.getMaxStack());
        for (int left = count; left > 0; left -= stackSize) {
            try {
                SimpleItemContainer.addOrDropItemStack(
                        store, player, combined(store, player), new ItemStack(itemId, Math.min(stackSize, left)));
            } catch (RuntimeException e) {
                LOG.at(Level.SEVERE).withCause(e).log("hyornament: cutter could not give %s x%d", itemId, left);
                return;
            }
        }
    }

    private static ItemContainer combined(Store<EntityStore> store, Ref<EntityStore> player) {
        return InventoryComponent.getCombined(store, player, InventoryComponent.HOTBAR_FIRST);
    }
}
