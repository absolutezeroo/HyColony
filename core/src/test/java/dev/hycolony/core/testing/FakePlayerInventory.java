package dev.hycolony.core.testing;

import dev.hycolony.core.kernel.item.ItemAmount;
import dev.hycolony.core.kernel.item.ItemKey;
import dev.hycolony.core.kernel.port.PlayerInventory;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Unlimited-capacity player inventories, keyed by player. Damaged stacks (worn tools) sit in {@link #worn} and are
 * taken after the undamaged ones.
 */
public final class FakePlayerInventory implements PlayerInventory {
    public final Map<UUID, Map<ItemKey, Integer>> inventories = new LinkedHashMap<>();
    public final Map<UUID, List<ItemAmount>> worn = new LinkedHashMap<>();

    @Override
    public int count(UUID player, ItemKey item) {
        return contents(player).getOrDefault(item, 0);
    }

    @Override
    public List<ItemAmount> takeStacks(UUID player, ItemKey item, int max) {
        List<ItemAmount> out = new ArrayList<>();
        Map<ItemKey, Integer> inv = inventories.getOrDefault(player, new LinkedHashMap<>());
        int have = inv.getOrDefault(item, 0);
        int taken = Math.min(have, max);
        if (taken > 0) {
            if (taken == have) {
                inv.remove(item);
            } else {
                inv.put(item, have - taken);
            }
            out.add(new ItemAmount(item, taken));
        }
        Iterator<ItemAmount> it = worn.getOrDefault(player, new ArrayList<>()).iterator();
        while (it.hasNext() && taken < max) {
            ItemAmount a = it.next();
            if (a.item().equals(item) && a.count() <= max - taken) {
                it.remove();
                out.add(a);
                taken += a.count();
            }
        }
        return out;
    }

    @Override
    public Map<ItemKey, Integer> contents(UUID player) {
        Map<ItemKey, Integer> out = new LinkedHashMap<>(inventories.getOrDefault(player, Map.of()));
        for (ItemAmount a : worn.getOrDefault(player, List.of())) {
            out.merge(a.item(), a.count(), Integer::sum);
        }
        return out;
    }

    @Override
    public ItemAmount give(UUID player, ItemAmount amount) {
        if (amount.damage() > 0) {
            worn.computeIfAbsent(player, p -> new ArrayList<>()).add(amount);
            return null;
        }
        inventories
                .computeIfAbsent(player, p -> new LinkedHashMap<>())
                .merge(amount.item(), amount.count(), Integer::sum);
        return null;
    }
}
