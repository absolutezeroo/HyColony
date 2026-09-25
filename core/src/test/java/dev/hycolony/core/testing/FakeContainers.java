package dev.hycolony.core.testing;

import dev.hycolony.core.kernel.BlockPos;
import dev.hycolony.core.kernel.item.ItemAmount;
import dev.hycolony.core.kernel.item.ItemKey;
import dev.hycolony.core.kernel.port.ContainerAccess;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Unlimited-capacity containers, keyed by block position. */
public final class FakeContainers implements ContainerAccess {
    public final Map<BlockPos, Map<ItemKey, Integer>> containers = new LinkedHashMap<>();

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
        if (positions.isEmpty()) {
            return amount;
        }
        Map<ItemKey, Integer> c = containers.computeIfAbsent(positions.get(0), p -> new LinkedHashMap<>());
        c.merge(amount.item(), amount.count(), Integer::sum);
        return null;
    }

    @Override
    public Map<ItemKey, Integer> contents(List<BlockPos> positions) {
        Map<ItemKey, Integer> total = new LinkedHashMap<>();
        for (BlockPos pos : positions) {
            containers.getOrDefault(pos, Map.of()).forEach((k, v) -> total.merge(k, v, Integer::sum));
        }
        return total;
    }
}
