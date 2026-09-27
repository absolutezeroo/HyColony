package dev.hycolony.core.logistics.pickup;

import dev.hycolony.core.kernel.item.ItemKey;
import java.util.function.Predicate;

/**
 * Up to {@code amount} of the items {@code matches} accepts stay in a building (MC {@code keepX} entry: predicate to
 * {@code Tuple<amount, inventory>}). {@code inventory} also keeps them in the worker's inventory on a dump; otherwise
 * they are only kept in the hut's racks.
 */
public record KeepRule(Predicate<ItemKey> matches, int amount, boolean inventory) {}
