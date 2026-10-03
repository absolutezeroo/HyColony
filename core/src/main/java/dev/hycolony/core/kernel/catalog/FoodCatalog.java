package dev.hycolony.core.kernel.catalog;

import dev.hycolony.core.kernel.item.FoodInfo;
import dev.hycolony.core.kernel.item.ItemKey;
import java.util.List;
import java.util.Optional;

/** What the game says of food: what eating an item gives, and what cooking it makes. */
public interface FoodCatalog {
    /** What eating {@code item} gives (MC ItemStackUtils.ISFOOD and FoodProperties); empty for no food. */
    Optional<FoodInfo> food(ItemKey item);

    /** Every item {@link #food} knows, in a set order (MC CompatibilityManager's edibles, before filtering). */
    List<ItemKey> foods();

    /**
     * What cooking {@code item} gives (MC the furnace's smelting result; in Hytale the campfire's); empty when it
     * does not cook.
     */
    Optional<ItemKey> cooked(ItemKey item);
}
