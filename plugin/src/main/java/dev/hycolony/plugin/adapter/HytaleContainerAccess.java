package dev.hycolony.plugin.adapter;

import com.hypixel.hytale.logger.HytaleLogger;
import com.hypixel.hytale.server.core.asset.type.item.config.Item;
import com.hypixel.hytale.server.core.inventory.ItemStack;
import com.hypixel.hytale.server.core.inventory.container.ItemContainer;
import com.hypixel.hytale.server.core.modules.block.BlockModule;
import com.hypixel.hytale.server.core.modules.block.components.ItemContainerBlock;
import com.hypixel.hytale.server.core.universe.world.World;
import dev.hycolony.core.kernel.BlockPos;
import dev.hycolony.core.kernel.item.ItemAmount;
import dev.hycolony.core.kernel.item.ItemKey;
import dev.hycolony.core.kernel.port.ContainerAccess;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Level;

/**
 * ContainerAccess over {@link ItemContainerBlock} (cheat sheet § 3). An unloaded chunk or a position without a
 * container is skipped. Items are matched by id and removed by slot, never by {@code ItemStack} equality (a worn
 * tool would not match). World thread only.
 */
public final class HytaleContainerAccess implements ContainerAccess {
    private static final HytaleLogger LOG = HytaleLogger.forEnclosingClass();

    private final World world;
    private boolean warned;

    public HytaleContainerAccess(World world) {
        this.world = world;
    }

    @Override
    public int count(List<BlockPos> containers, ItemKey item) {
        try {
            int total = 0;
            for (BlockPos p : containers) {
                ItemContainer c = container(p);
                if (c != null) {
                    total += c.countItemStacks(s -> s.getItemId().equals(item.id()));
                }
            }
            return total;
        } catch (RuntimeException e) {
            fail("count", e);
            return 0;
        }
    }

    @Override
    public int extract(List<BlockPos> containers, ItemKey item, int max) {
        int taken = 0;
        try {
            for (BlockPos p : containers) {
                if (taken >= max) {
                    break;
                }
                ItemContainer c = container(p);
                if (c != null) {
                    taken += takeBySlot(c, item, max - taken);
                }
            }
        } catch (RuntimeException e) {
            fail("extract", e); // what was already taken stays taken: report it
        }
        return taken;
    }

    @Override
    public ItemAmount insert(List<BlockPos> containers, ItemAmount amount) {
        if (Item.getAssetMap().getAsset(amount.item().id()) == null) {
            return amount; // unknown item: nothing fits
        }
        ItemAmount left = amount;
        try {
            for (BlockPos p : containers) {
                ItemContainer c = container(p);
                if (c != null) {
                    left = give(c, left);
                    if (left == null) {
                        return null;
                    }
                }
            }
        } catch (RuntimeException e) {
            fail("insert", e);
        }
        return left;
    }

    @Override
    public Map<ItemKey, Integer> contents(List<BlockPos> containers) {
        Map<ItemKey, Integer> out = new LinkedHashMap<>();
        try {
            for (BlockPos p : containers) {
                ItemContainer c = container(p);
                if (c != null) {
                    addContents(c, out);
                }
            }
        } catch (RuntimeException e) {
            fail("contents", e);
        }
        return out;
    }

    private ItemContainer container(BlockPos p) {
        ItemContainerBlock b = BlockModule.getComponent(ItemContainerBlock.getComponentType(), world, p.x(), p.y(), p.z());
        return b == null ? null : b.getItemContainer();
    }

    /** Removes up to {@code max} of {@code item}, slot by slot. Returns how many were removed. */
    static int takeBySlot(ItemContainer c, ItemKey item, int max) {
        int taken = 0;
        for (short s = 0; s < c.getCapacity() && taken < max; s++) {
            ItemStack st = c.getItemStack(s);
            if (ItemStack.isEmpty(st) || !st.getItemId().equals(item.id())) {
                continue;
            }
            int n = Math.min(max - taken, st.getQuantity());
            if (c.removeItemStackFromSlot(s, n).succeeded()) {
                taken += n;
            }
        }
        return taken;
    }

    /** Adds {@code a} to {@code c}. Returns the remainder, or null if everything fit. */
    static ItemAmount give(ItemContainer c, ItemAmount a) {
        ItemStack rem = c.addItemStack(new ItemStack(a.item().id(), a.count())).getRemainder();
        return ItemStack.isEmpty(rem) ? null : a.withCount(rem.getQuantity());
    }

    /** Merges {@code c}'s stacks into {@code out} by item id, in slot order. */
    static void addContents(ItemContainer c, Map<ItemKey, Integer> out) {
        c.forEach((slot, s) -> out.merge(new ItemKey(s.getItemId()), s.getQuantity(), Integer::sum));
    }

    private void fail(String op, RuntimeException e) {
        LOG.at(warned ? Level.FINE : Level.WARNING).withCause(e).log("ContainerAccess.%s failed", op);
        warned = true;
    }
}
