package dev.hycolony.core.kernel.port;

import dev.hycolony.core.kernel.item.ItemAmount;
import dev.hycolony.core.kernel.item.ItemKey;
import java.util.Map;
import java.util.UUID;
import org.jspecify.annotations.Nullable;

public interface PlayerInventory {
    int count(UUID player, ItemKey item);

    /** Returns how much was actually taken. */
    int take(UUID player, ItemKey item, int max);

    /** Everything the player carries, in inventory order. */
    Map<ItemKey, Integer> contents(UUID player);

    /** Returns the remainder that did not fit, or {@code null} if everything was given. */
    @Nullable
    ItemAmount give(UUID player, ItemAmount amount);
}
