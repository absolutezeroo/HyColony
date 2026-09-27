package dev.hycolony.core.kernel.port;

import dev.hycolony.core.kernel.item.ItemAmount;
import dev.hycolony.core.kernel.item.ItemKey;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.jspecify.annotations.Nullable;

public interface PlayerInventory {
    int count(UUID player, ItemKey item);

    /** Returns how much was actually taken (see {@link #takeStacks}). */
    default int take(UUID player, ItemKey item, int max) {
        return takeStacks(player, item, max).stream()
                .mapToInt(ItemAmount::count)
                .sum();
    }

    /** Takes up to {@code max} of {@code item}; returns the stacks taken, each with its damage. */
    List<ItemAmount> takeStacks(UUID player, ItemKey item, int max);

    /** Everything the player carries, in inventory order. */
    Map<ItemKey, Integer> contents(UUID player);

    /** Gives {@code amount} with its damage. Returns the remainder that did not fit, or {@code null} if all fit. */
    @Nullable
    ItemAmount give(UUID player, ItemAmount amount);
}
