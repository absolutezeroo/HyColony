package dev.hycolony.core.kernel.port;

import dev.hycolony.core.kernel.item.ItemAmount;
import dev.hycolony.core.kernel.item.ItemKey;
import java.util.UUID;

public interface PlayerInventory {
    int count(UUID player, ItemKey item);

    /** Returns how much was actually taken. */
    int take(UUID player, ItemKey item, int max);

    /** Returns the remainder that did not fit, or {@code null} if everything was given. */
    ItemAmount give(UUID player, ItemAmount amount);
}
