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
import java.util.function.Predicate;

/**
 * Unlimited-capacity player inventories, keyed by player. Damaged stacks (worn tools) sit in {@link #worn} and are
 * taken before the undamaged ones (a broken tool in an earlier slot).
 */
public final class FakePlayerInventory implements PlayerInventory {
    public final Map<UUID, Map<ItemKey, Integer>> inventories = new LinkedHashMap<>();
    public final Map<UUID, List<ItemAmount>> worn = new LinkedHashMap<>();

    @Override
    public int count(UUID player, ItemKey item) {
        return contents(player).getOrDefault(item, 0);
    }

    @Override
    public List<ItemAmount> takeStacks(UUID player, ItemKey item, int max, Predicate<ItemAmount> accept) {
        List<ItemAmount> out = new ArrayList<>();
        int taken = 0;
        Iterator<ItemAmount> it = worn.getOrDefault(player, new ArrayList<>()).iterator();
        while (it.hasNext() && taken < max) {
            ItemAmount a = it.next();
            if (a.item().equals(item) && a.count() <= max - taken && accept.test(a)) {
                it.remove();
                out.add(a);
                taken += a.count();
            }
        }
        Map<ItemKey, Integer> inv = inventories.getOrDefault(player, new LinkedHashMap<>());
        int have = inv.getOrDefault(item, 0);
        int take = Math.min(have, max - taken);
        if (take > 0 && accept.test(new ItemAmount(item, take))) {
            if (take == have) {
                inv.remove(item);
            } else {
                inv.put(item, have - take);
            }
            out.add(new ItemAmount(item, take));
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
