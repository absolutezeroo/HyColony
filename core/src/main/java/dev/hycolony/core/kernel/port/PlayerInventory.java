package dev.hycolony.core.kernel.port;

import dev.hycolony.core.kernel.item.ItemAmount;
import dev.hycolony.core.kernel.item.ItemKey;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Predicate;
import org.jspecify.annotations.Nullable;

public interface PlayerInventory {
    int count(UUID player, ItemKey item);

    /** Returns how much was actually taken (see {@link #takeStacks}). */
    default int take(UUID player, ItemKey item, int max) {
        return takeStacks(player, item, max).stream()
                .mapToInt(ItemAmount::count)
                .sum();
    }

    /** {@link #takeStacks(UUID, ItemKey, int, Predicate)} of any stack. */
    default List<ItemAmount> takeStacks(UUID player, ItemKey item, int max) {
        return takeStacks(player, item, max, _ -> true);
    }

    /**
     * Takes up to {@code max} of {@code item} from the slots whose stack {@code accept}s; returns the stacks taken,
     * each with its damage.
     */
    List<ItemAmount> takeStacks(UUID player, ItemKey item, int max, Predicate<ItemAmount> accept);

    /** Every stack the player carries (not what they wear), with its damage, in inventory order. */
    List<ItemAmount> stacks(UUID player);

    /** The stacks the player wears or holds aside, with their damage: MC's armour and shield slots. */
    List<ItemAmount> equipped(UUID player);

    /** Everything the player carries, by item, in inventory order. */
    default Map<ItemKey, Integer> contents(UUID player) {
        Map<ItemKey, Integer> out = new LinkedHashMap<>();
        stacks(player).forEach(s -> out.merge(s.item(), s.count(), Integer::sum));
        return out;
    }

    /** Gives {@code amount} with its damage. Returns the remainder that did not fit, or {@code null} if all fit. */
    @Nullable
    ItemAmount give(UUID player, ItemAmount amount);

    /**
     * MC SwitchBuildingWithToolMessage: the last hotbar slot holding {@code hotbarItem} and the last slot of the whole
     * inventory holding {@code otherItem} swap their stacks; false, changing nothing, without one of them.
     */
    boolean swapIntoHotbar(UUID player, ItemKey hotbarItem, ItemKey otherItem);
}
