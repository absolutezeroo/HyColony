package dev.hycolony.core.kernel.port;

import dev.hycolony.core.kernel.BlockPos;
import dev.hycolony.core.kernel.item.ItemAmount;
import dev.hycolony.core.kernel.item.ItemKey;
import java.util.List;
import java.util.Map;

public interface ContainerAccess {
    int count(List<BlockPos> containers, ItemKey item);

    int extract(List<BlockPos> containers, ItemKey item, int max);

    /** Returns the remainder that did not fit, or {@code null} if everything was inserted. */
    ItemAmount insert(List<BlockPos> containers, ItemAmount amount);

    Map<ItemKey, Integer> contents(List<BlockPos> containers);
}
