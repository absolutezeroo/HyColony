package dev.hycolony.core.testing;

import dev.hycolony.core.kernel.item.ItemAmount;
import dev.hycolony.core.kernel.item.ItemKey;
import dev.hycolony.core.kernel.port.PlayerInventory;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Predicate;

/**
 * Unlimited-capacity player inventories, keyed by player, except the {@link #full} ones that take nothing. Damaged
 * stacks (worn tools) sit in {@link #damaged} and are taken before the undamaged ones (a broken tool in an earlier
 * slot).
 */
public final class FakePlayerInventory implements PlayerInventory {
    public final Map<UUID, Map<ItemKey, Integer>> inventories = new LinkedHashMap<>();
    public final Map<UUID, List<ItemAmount>> damaged = new LinkedHashMap<>();
    /** What each player wears (armour, utility slots): counted, never taken. */
    public final Map<UUID, List<ItemAmount>> equipped = new LinkedHashMap<>();

    public final Set<UUID> full = new HashSet<>();

    @Override
    public int count(UUID player, ItemKey item) {
        return contents(player).getOrDefault(item, 0);
    }

    @Override
    public List<ItemAmount> takeStacks(UUID player, ItemKey item, int max, Predicate<ItemAmount> accept) {
        List<ItemAmount> out = new ArrayList<>();
        int taken = 0;
        Iterator<ItemAmount> it =
                damaged.getOrDefault(player, new ArrayList<>()).iterator();
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

    /** The damaged stacks first, as {@link #takeStacks} takes them, then one undamaged stack per item. */
    @Override
    public List<ItemAmount> stacks(UUID player) {
        List<ItemAmount> out = new ArrayList<>(damaged.getOrDefault(player, List.of()));
        inventories.getOrDefault(player, Map.of()).forEach((item, n) -> out.add(new ItemAmount(item, n)));
        return out;
    }

    @Override
    public List<ItemAmount> equipped(UUID player) {
        return List.copyOf(equipped.getOrDefault(player, List.of()));
    }

    /** Each successful swapIntoHotbar, as "hotbarItem<->otherItem"; this fake has no slots, so nothing moves. */
    public final List<String> swaps = new ArrayList<>();

    @Override
    public boolean swapIntoHotbar(UUID player, ItemKey hotbarItem, ItemKey otherItem) {
        if (count(player, hotbarItem) < 1 || count(player, otherItem) < 1) {
            return false;
        }
        swaps.add(hotbarItem + "<->" + otherItem);
        return true;
    }

    @Override
    public ItemAmount give(UUID player, ItemAmount amount) {
        if (full.contains(player)) {
            return amount;
        }
        if (amount.damage() > 0) {
            damaged.computeIfAbsent(player, p -> new ArrayList<>()).add(amount);
            return null;
        }
        inventories
                .computeIfAbsent(player, p -> new LinkedHashMap<>())
                .merge(amount.item(), amount.count(), Integer::sum);
        return null;
    }
}
