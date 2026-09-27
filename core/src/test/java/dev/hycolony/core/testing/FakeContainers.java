package dev.hycolony.core.testing;

import dev.hycolony.core.kernel.BlockPos;
import dev.hycolony.core.kernel.item.ItemAmount;
import dev.hycolony.core.kernel.item.ItemKey;
import dev.hycolony.core.kernel.port.ContainerAccess;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Containers keyed by block position; {@link #full} makes every insert fail. A position listed in {@link #slots} holds
 * that many slots of at most {@link #maxStack} items each (unlimited by default, so one slot per distinct item) and an
 * insert there can fit partly, like a real container; any other position is unlimited.
 */
public final class FakeContainers implements ContainerAccess {
    public final Map<BlockPos, Map<ItemKey, Integer>> containers = new LinkedHashMap<>();
    public final Map<BlockPos, Integer> slots = new HashMap<>();
    public int maxStack = Integer.MAX_VALUE;
    public boolean full;

    @Override
    public int count(List<BlockPos> positions, ItemKey item) {
        int total = 0;
        for (BlockPos pos : positions) {
            total += containers.getOrDefault(pos, Map.of()).getOrDefault(item, 0);
        }
        return total;
    }

    @Override
    public int extract(List<BlockPos> positions, ItemKey item, int max) {
        int removed = 0;
        for (BlockPos pos : positions) {
            if (removed >= max) {
                break;
            }
            Map<ItemKey, Integer> c = containers.get(pos);
            if (c == null) {
                continue;
            }
            int have = c.getOrDefault(item, 0);
            int take = Math.min(have, max - removed);
            if (take <= 0) {
                continue;
            }
            if (take == have) {
                c.remove(item);
            } else {
                c.put(item, have - take);
            }
            removed += take;
        }
        return removed;
    }

    @Override
    public ItemAmount insert(List<BlockPos> positions, ItemAmount amount) {
        if (positions.isEmpty() || full) {
            return amount;
        }
        BlockPos pos = positions.get(0);
        Map<ItemKey, Integer> c = containers.computeIfAbsent(pos, p -> new LinkedHashMap<>());
        int fits = slots.containsKey(pos) ? (int) Math.min(amount.count(), room(pos, amount.item())) : amount.count();
        if (fits > 0) {
            c.merge(amount.item(), fits, Integer::sum);
        }
        return fits == amount.count() ? null : amount.withCount(amount.count() - fits);
    }

    @Override
    public Map<ItemKey, Integer> contents(List<BlockPos> positions) {
        Map<ItemKey, Integer> total = new LinkedHashMap<>();
        for (BlockPos pos : positions) {
            containers.getOrDefault(pos, Map.of()).forEach((k, v) -> total.merge(k, v, Integer::sum));
        }
        return total;
    }

    @Override
    public int freeSlots(BlockPos container) {
        Integer capacity = slots.get(container);
        if (capacity == null) {
            return Integer.MAX_VALUE;
        }
        long used = 0;
        for (int count : containers.getOrDefault(container, Map.of()).values()) {
            used += (count + (long) maxStack - 1) / maxStack;
        }
        return (int) Math.max(0, capacity - used);
    }

    /** How many more of {@code item} fit at {@code pos}: the top of its last stack, then the free slots. */
    private long room(BlockPos pos, ItemKey item) {
        int have = containers.getOrDefault(pos, Map.of()).getOrDefault(item, 0);
        long top = have == 0 ? 0 : ((have + (long) maxStack - 1) / maxStack) * maxStack - have;
        return top + (long) freeSlots(pos) * maxStack;
    }
}
