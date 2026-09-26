package dev.hycolony.core.testing;

import dev.hycolony.core.kernel.item.ItemAmount;
import dev.hycolony.core.kernel.item.ItemKey;
import dev.hycolony.core.kernel.port.PlayerInventory;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/** Unlimited-capacity player inventories, keyed by player. */
public final class FakePlayerInventory implements PlayerInventory {
    public final Map<UUID, Map<ItemKey, Integer>> inventories = new LinkedHashMap<>();

    @Override
    public int count(UUID player, ItemKey item) {
        return inventories.getOrDefault(player, Map.of()).getOrDefault(item, 0);
    }

    @Override
    public int take(UUID player, ItemKey item, int max) {
        Map<ItemKey, Integer> inv = inventories.get(player);
        if (inv == null) {
            return 0;
        }
        int have = inv.getOrDefault(item, 0);
        int take = Math.min(have, max);
        if (take <= 0) {
            return 0;
        }
        if (take == have) {
            inv.remove(item);
        } else {
            inv.put(item, have - take);
        }
        return take;
    }

    @Override
    public Map<ItemKey, Integer> contents(UUID player) {
        return new LinkedHashMap<>(inventories.getOrDefault(player, Map.of()));
    }

    @Override
    public ItemAmount give(UUID player, ItemAmount amount) {
        inventories
                .computeIfAbsent(player, p -> new LinkedHashMap<>())
                .merge(amount.item(), amount.count(), Integer::sum);
        return null;
    }
}
