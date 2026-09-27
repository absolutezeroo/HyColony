package dev.hycolony.core.testing;

import dev.hycolony.core.kernel.BlockPos;
import dev.hycolony.core.kernel.item.ItemAmount;
import dev.hycolony.core.kernel.item.ItemKey;
import dev.hycolony.core.kernel.port.ContainerAccess;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Predicate;

/**
 * Containers keyed by block position; {@link #full} makes every insert fail. A position listed in {@link #slots} holds
 * that many slots of at most {@link #maxStack} items each (unlimited by default, so one slot per distinct item) and an
 * insert there can fit partly, like a real container; any other position is unlimited. Damaged stacks (worn tools) sit
 * in {@link #worn}, one slot each, listed and taken before the undamaged ones (a broken tool in an earlier slot); a
 * test may put an undamaged stack there too, to set the slot order.
 */
public final class FakeContainers implements ContainerAccess {
    public final Map<BlockPos, Map<ItemKey, Integer>> containers = new LinkedHashMap<>();
    public final Map<BlockPos, List<ItemAmount>> worn = new LinkedHashMap<>();
    public final Map<BlockPos, Integer> slots = new HashMap<>();
    public int maxStack = Integer.MAX_VALUE;
    public boolean full;

    @Override
    public int count(List<BlockPos> positions, ItemKey item) {
        int total = 0;
        for (BlockPos pos : positions) {
            total += containers.getOrDefault(pos, Map.of()).getOrDefault(item, 0);
            for (ItemAmount a : worn.getOrDefault(pos, List.of())) {
                total += a.item().equals(item) ? a.count() : 0;
            }
        }
        return total;
    }

    @Override
    public List<ItemAmount> extractStacks(
            List<BlockPos> positions, ItemKey item, int max, Predicate<ItemAmount> accept) {
        List<ItemAmount> out = new ArrayList<>();
        int removed = 0;
        for (BlockPos pos : positions) {
            removed += takeWorn(pos, item, max - removed, accept, out);
            Map<ItemKey, Integer> c = containers.get(pos);
            int have = c == null ? 0 : c.getOrDefault(item, 0);
            int take = Math.min(have, max - removed);
            if (c != null && take > 0 && accept.test(new ItemAmount(item, take))) {
                if (take == have) {
                    c.remove(item);
                } else {
                    c.put(item, have - take);
                }
                out.add(new ItemAmount(item, take));
                removed += take;
            }
        }
        return out;
    }

    /** Takes whole worn stacks of {@code item} at {@code pos}, up to {@code max} items, into {@code out}. */
    private int takeWorn(BlockPos pos, ItemKey item, int max, Predicate<ItemAmount> accept, List<ItemAmount> out) {
        int removed = 0;
        Iterator<ItemAmount> it = worn.getOrDefault(pos, new ArrayList<>()).iterator();
        while (it.hasNext() && removed < max) {
            ItemAmount a = it.next();
            if (a.item().equals(item) && a.count() <= max - removed && accept.test(a)) {
                it.remove();
                out.add(a);
                removed += a.count();
            }
        }
        return removed;
    }

    @Override
    public ItemAmount insert(List<BlockPos> positions, ItemAmount amount) {
        if (positions.isEmpty() || full) {
            return amount;
        }
        BlockPos pos = positions.get(0);
        if (amount.damage() > 0) {
            if (slots.containsKey(pos) && freeSlots(pos) <= 0) {
                return amount;
            }
            worn.computeIfAbsent(pos, p -> new ArrayList<>()).add(amount);
            return null;
        }
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
            for (ItemAmount a : worn.getOrDefault(pos, List.of())) {
                total.merge(a.item(), a.count(), Integer::sum);
            }
        }
        return total;
    }

    @Override
    public int freeSlots(BlockPos container) {
        Integer capacity = slots.get(container);
        if (capacity == null) {
            return Integer.MAX_VALUE;
        }
        long used = worn.getOrDefault(container, List.of()).size();
        for (int count : containers.getOrDefault(container, Map.of()).values()) {
            used += (count + (long) maxStack - 1) / maxStack;
        }
        return (int) Math.max(0, capacity - used);
    }

    /** One slot per item, split into stacks of {@link #maxStack} on the positions listed in {@link #slots}. */
    @Override
    public List<ItemAmount> stacks(BlockPos container) {
        int size = slots.containsKey(container) ? maxStack : Integer.MAX_VALUE;
        List<ItemAmount> out = new ArrayList<>(worn.getOrDefault(container, List.of()));
        containers.getOrDefault(container, Map.of()).forEach((item, count) -> {
            for (int left = count; left > 0; left -= size) {
                out.add(new ItemAmount(item, Math.min(left, size)));
            }
        });
        return out;
    }

    /** How many more of {@code item} fit at {@code pos}: the top of its last stack, then the free slots. */
    private long room(BlockPos pos, ItemKey item) {
        int have = containers.getOrDefault(pos, Map.of()).getOrDefault(item, 0);
        long top = have == 0 ? 0 : ((have + (long) maxStack - 1) / maxStack) * maxStack - have;
        return top + (long) freeSlots(pos) * maxStack;
    }
}
