package dev.hycolony.core.crafting.furnace;

import dev.hycolony.core.kernel.item.BlockKey;
import dev.hycolony.core.kernel.item.ItemKey;
import java.util.List;
import java.util.Optional;

/**
 * What cooking asks of the game's content: the station block (MC's furnace, Hytale's campfire), the fuels and which
 * raw item cooks into a dish. Never throws: an unknown block or item is no station, no fuel, no dish.
 */
public interface CookingCatalog {
    /** Whether {@code block} is a cooking station a waiter fills (MC FurnaceBlock; Hytale's campfire bench). */
    boolean isStation(BlockKey block);

    /** Every item that burns in a station (MC the burnable materials; Hytale's Fuel resource type), in a set order. */
    List<ItemKey> fuels();

    /** The fuels a new list allows (MC ItemListModule's default: coal and charcoal). */
    List<ItemKey> defaultFuels();

    /** The first item cooking turns into {@code dish} (MC getFirstSmeltingRecipeByResult's input); empty for none. */
    Optional<ItemKey> rawFor(ItemKey dish);
}
