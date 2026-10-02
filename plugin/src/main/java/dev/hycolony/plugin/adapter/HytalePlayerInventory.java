package dev.hycolony.plugin.adapter;

import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.logger.HytaleLogger;
import com.hypixel.hytale.server.core.asset.type.item.config.Item;
import com.hypixel.hytale.server.core.inventory.InventoryComponent;
import com.hypixel.hytale.server.core.inventory.ItemStack;
import com.hypixel.hytale.server.core.inventory.container.ItemContainer;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.Universe;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import dev.hycolony.core.kernel.item.ItemAmount;
import dev.hycolony.core.kernel.item.ItemKey;
import dev.hycolony.core.kernel.port.PlayerInventory;
import dev.hycolony.plugin.item.HytaleStacks;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Predicate;
import java.util.logging.Level;
import org.jspecify.annotations.Nullable;

/**
 * PlayerInventory over the player's hotbar then storage (cheat sheet § 4, {@code InventoryComponent.HOTBAR_FIRST}).
 * A player who is offline or in another world has nothing: 0, empty, or the full remainder. Items are taken by slot
 * and id, with their damage, like {@link HytaleContainerAccess}. World thread only.
 */
public final class HytalePlayerInventory implements PlayerInventory {
    private static final HytaleLogger LOG = HytaleLogger.forEnclosingClass();

    private final World world;
    private final HytaleStacks stacks;
    private boolean warned;

    public HytalePlayerInventory(World world, HytaleStacks stacks) {
        this.world = world;
        this.stacks = stacks;
    }

    @Override
    public int count(UUID player, ItemKey item) {
        try {
            ItemContainer c = inventory(player);
            return c == null ? 0 : c.countItemStacks(s -> s.getItemId().equals(item.id()));
        } catch (RuntimeException e) {
            fail("count", e);
            return 0;
        }
    }

    @Override
    public List<ItemAmount> takeStacks(UUID player, ItemKey item, int max, Predicate<ItemAmount> accept) {
        List<ItemAmount> out = new ArrayList<>();
        try {
            ItemContainer c = inventory(player);
            if (c != null) {
                HytaleContainerAccess.takeBySlot(c, new HytaleContainerAccess.Take(item, max, accept), stacks, out);
            }
        } catch (RuntimeException e) {
            fail("take", e); // what was already taken stays taken: report it
        }
        return out;
    }

    @Override
    public Map<ItemKey, Integer> contents(UUID player) {
        Map<ItemKey, Integer> out = new LinkedHashMap<>();
        try {
            ItemContainer c = inventory(player);
            if (c != null) {
                HytaleContainerAccess.addContents(c, out);
            }
        } catch (RuntimeException e) {
            fail("contents", e);
        }
        return out;
    }

    @Override
    public @Nullable ItemAmount give(UUID player, ItemAmount amount) {
        try {
            ItemContainer c = inventory(player);
            if (c == null || Item.getAssetMap().getAsset(amount.item().id()) == null) {
                return amount;
            }
            return HytaleContainerAccess.give(c, amount, stacks);
        } catch (RuntimeException e) {
            fail("give", e);
            return amount;
        }
    }

    /**
     * MC SwitchBuildingWithToolMessage on the hotbar then storage: the last hotbar slot holding {@code hotbarItem} and
     * the last slot holding {@code otherItem} swap their stacks, each only if it still holds what was read.
     */
    @Override
    public boolean swapIntoHotbar(UUID player, ItemKey hotbarItem, ItemKey otherItem) {
        try {
            Ref<EntityStore> ref = ref(player);
            if (ref == null) {
                return false;
            }
            Store<EntityStore> store = ref.getStore();
            InventoryComponent.Hotbar hotbar = store.getComponent(ref, InventoryComponent.Hotbar.getComponentType());
            ItemContainer all = InventoryComponent.getCombined(store, ref, InventoryComponent.HOTBAR_FIRST);
            short inHotbar = lastSlot(
                    all, hotbarItem, hotbar == null ? 0 : hotbar.getInventory().getCapacity());
            short other = lastSlot(all, otherItem, all.getCapacity());
            if (inHotbar < 0 || other < 0) {
                return false;
            }
            ItemStack a = all.getItemStack(inHotbar);
            ItemStack b = all.getItemStack(other);
            // The second write only once the first has succeeded: alone, it would duplicate the hut's stack.
            return all.replaceItemStackInSlot(inHotbar, a, b).succeeded()
                    && all.replaceItemStackInSlot(other, b, a).succeeded();
        } catch (RuntimeException e) {
            fail("swap", e);
            return false;
        }
    }

    /** The last of the first {@code size} slots of {@code c} holding {@code item}; -1 for none. */
    private static short lastSlot(ItemContainer c, ItemKey item, int size) {
        for (short slot = (short) (Math.min(size, c.getCapacity()) - 1); slot >= 0; slot--) {
            ItemStack s = c.getItemStack(slot);
            if (s != null && !s.isEmpty() && s.getItemId().equals(item.id())) {
                return slot;
            }
        }
        return -1;
    }

    /** The player's hotbar + storage, or null if offline or not in this world. */
    private @Nullable ItemContainer inventory(UUID player) {
        Ref<EntityStore> ref = ref(player);
        return ref == null
                ? null
                : InventoryComponent.getCombined(ref.getStore(), ref, InventoryComponent.HOTBAR_FIRST);
    }

    /** The player's entity in this world; null if offline or elsewhere. */
    private @Nullable Ref<EntityStore> ref(UUID player) {
        PlayerRef pr = Universe.get().getPlayer(player);
        Ref<EntityStore> ref = pr == null ? null : pr.getReference();
        Store<EntityStore> store = world.getEntityStore().getStore();
        return ref == null || !ref.isValid() || ref.getStore() != store ? null : ref;
    }

    private void fail(String op, RuntimeException e) {
        LOG.at(warned ? Level.FINE : Level.WARNING).withCause(e).log("PlayerInventory.%s failed", op);
        warned = true;
    }
}
