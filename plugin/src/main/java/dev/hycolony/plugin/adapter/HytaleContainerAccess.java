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
import dev.hycolony.plugin.item.HytaleStacks;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Predicate;
import java.util.logging.Level;
import org.jspecify.annotations.Nullable;

/**
 * ContainerAccess over {@link ItemContainerBlock} (cheat sheet § 3). An unloaded chunk or a position without a
 * container is skipped. Items are matched by id and removed by slot, never by {@code ItemStack} equality (a worn
 * tool would not match); a stack's durability travels as its damage ({@link HytaleStacks}). World thread only.
 */
public final class HytaleContainerAccess implements ContainerAccess {
    private static final HytaleLogger LOG = HytaleLogger.forEnclosingClass();

    private final World world;
    private final HytaleStacks stacks;
    private boolean warned;

    public HytaleContainerAccess(World world, HytaleStacks stacks) {
        this.world = world;
        this.stacks = stacks;
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
    public List<ItemAmount> extractStacks(
            List<BlockPos> containers, ItemKey item, int max, Predicate<ItemAmount> accept) {
        List<ItemAmount> out = new ArrayList<>();
        try {
            int taken = 0;
            for (BlockPos p : containers) {
                if (taken >= max) {
                    break;
                }
                ItemContainer c = container(p);
                if (c != null) {
                    taken += takeBySlot(c, new Take(item, max - taken, accept), stacks, out);
                }
            }
        } catch (RuntimeException e) {
            fail("extract", e); // what was already taken stays taken: report it
        }
        return out;
    }

    @Override
    public @Nullable ItemAmount insert(List<BlockPos> containers, ItemAmount amount) {
        if (Item.getAssetMap().getAsset(amount.item().id()) == null) {
            return amount; // unknown item: nothing fits
        }
        ItemAmount left = amount;
        try {
            for (BlockPos p : containers) {
                ItemContainer c = container(p);
                if (c != null) {
                    left = give(c, left, stacks);
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

    @Override
    public int freeSlots(BlockPos container) {
        try {
            ItemContainer c = container(container);
            if (c == null) {
                return 0;
            }
            int free = 0;
            for (short s = 0; s < c.getCapacity(); s++) {
                if (ItemStack.isEmpty(c.getItemStack(s))) {
                    free++;
                }
            }
            return free;
        } catch (RuntimeException e) {
            fail("freeSlots", e);
            return 0;
        }
    }

    @Override
    public List<ItemAmount> stacks(BlockPos container) {
        List<ItemAmount> out = new ArrayList<>();
        try {
            ItemContainer c = container(container);
            if (c != null) {
                c.forEach((slot, s) -> out.add(stacks.toAmount(s)));
            }
        } catch (RuntimeException e) {
            fail("stacks", e);
            return List.of();
        }
        return out;
    }

    private @Nullable ItemContainer container(BlockPos p) {
        ItemContainerBlock b =
                BlockModule.getComponent(ItemContainerBlock.getComponentType(), world, p.x(), p.y(), p.z());
        return b == null ? null : b.getItemContainer();
    }

    /** Up to {@code max} of {@code item}, from the slots whose stack {@code accept}s. */
    record Take(ItemKey item, int max, Predicate<ItemAmount> accept) {}

    /**
     * Removes what {@code take} asks, slot by slot, adding each part taken to {@code out} with its damage. Returns how
     * many were removed.
     */
    static int takeBySlot(ItemContainer c, Take take, HytaleStacks stacks, List<ItemAmount> out) {
        int taken = 0;
        for (short s = 0; s < c.getCapacity() && taken < take.max(); s++) {
            ItemStack st = c.getItemStack(s);
            if (st == null
                    || ItemStack.isEmpty(st)
                    || !st.getItemId().equals(take.item().id())
                    || !take.accept().test(stacks.toAmount(st))) {
                continue;
            }
            int n = Math.min(take.max() - taken, st.getQuantity());
            if (c.removeItemStackFromSlot(s, n).succeeded()) {
                taken += n;
                out.add(stacks.toAmount(st, n));
            }
        }
        return taken;
    }

    /** Adds {@code a}, with its damage, to {@code c}. Returns the remainder, or null if everything fit. */
    static @Nullable ItemAmount give(ItemContainer c, ItemAmount a, HytaleStacks stacks) {
        ItemStack rem = c.addItemStack(stacks.toStack(a)).getRemainder();
        return rem == null || ItemStack.isEmpty(rem) ? null : a.withCount(rem.getQuantity());
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
