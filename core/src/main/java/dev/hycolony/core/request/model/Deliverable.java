package dev.hycolony.core.request.model;

import dev.hycolony.core.kernel.item.ItemAmount;
import dev.hycolony.core.kernel.item.ItemKey;
import dev.hycolony.core.kernel.port.ItemCatalog;

/** A requestable satisfied by handing over items (MineColonies IDeliverable). */
public sealed interface Deliverable extends Requestable permits StackRequest, ToolRequest, StackList {
    boolean matches(ItemKey item, ItemCatalog catalog);

    /** {@link #matches(ItemKey, ItemCatalog)} for a real stack; a worn-out one ({@link ItemCatalog#wornOut}) never. */
    default boolean matches(ItemAmount stack, ItemCatalog catalog) {
        return !catalog.wornOut(stack) && matches(stack.item(), catalog);
    }

    int count();

    int minCount();

    Deliverable withCount(int count);

    boolean canBeResolvedByBuilding();
}
