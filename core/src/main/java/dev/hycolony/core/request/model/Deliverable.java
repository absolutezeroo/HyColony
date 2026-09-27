package dev.hycolony.core.request.model;

import dev.hycolony.core.kernel.item.ItemKey;
import dev.hycolony.core.kernel.port.ItemCatalog;

/** A requestable satisfied by handing over items (MineColonies IDeliverable). */
public sealed interface Deliverable extends Requestable permits StackRequest, ToolRequest {
    boolean matches(ItemKey item, ItemCatalog catalog);

    int count();

    int minCount();

    Deliverable withCount(int count);

    boolean canBeResolvedByBuilding();
}
