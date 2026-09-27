package dev.hycolony.core.kernel.port;

import dev.hycolony.core.kernel.BlockPos;
import dev.hycolony.core.kernel.item.ItemAmount;
import dev.hycolony.core.kernel.item.ItemKey;
import java.util.List;
import java.util.Map;
import org.jspecify.annotations.Nullable;

public interface ContainerAccess {
    int count(List<BlockPos> containers, ItemKey item);

    int extract(List<BlockPos> containers, ItemKey item, int max);

    /** Returns the remainder that did not fit, or {@code null} if everything was inserted. */
    @Nullable
    ItemAmount insert(List<BlockPos> containers, ItemAmount amount);

    Map<ItemKey, Integer> contents(List<BlockPos> containers);

    /** Empty slots of the container at {@code container}; 0 when there is none or its chunk is not loaded. */
    int freeSlots(BlockPos container);

    /**
     * The non-empty slots of the container at {@code container}, in slot order; empty when there is none or its chunk
     * is not loaded.
     */
    List<ItemAmount> stacks(BlockPos container);
}
