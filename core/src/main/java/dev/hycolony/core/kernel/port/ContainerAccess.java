package dev.hycolony.core.kernel.port;

import dev.hycolony.core.kernel.BlockPos;
import dev.hycolony.core.kernel.item.ItemAmount;
import dev.hycolony.core.kernel.item.ItemKey;
import java.util.List;
import java.util.Map;
import org.jspecify.annotations.Nullable;

public interface ContainerAccess {
    int count(List<BlockPos> containers, ItemKey item);

    /** Removes up to {@code max} of {@code item}; returns how many were removed (see {@link #extractStacks}). */
    default int extract(List<BlockPos> containers, ItemKey item, int max) {
        return extractStacks(containers, item, max).stream()
                .mapToInt(ItemAmount::count)
                .sum();
    }

    /**
     * Removes up to {@code max} of {@code item}; returns the stacks removed, each with its damage, so a worn tool stays
     * worn wherever it goes. Empty when there is none or the chunks are not loaded.
     */
    List<ItemAmount> extractStacks(List<BlockPos> containers, ItemKey item, int max);

    /**
     * Inserts {@code amount} with its damage. Returns the remainder that did not fit, or {@code null} if everything was
     * inserted.
     */
    @Nullable
    ItemAmount insert(List<BlockPos> containers, ItemAmount amount);

    Map<ItemKey, Integer> contents(List<BlockPos> containers);

    /** Empty slots of the container at {@code container}; 0 when there is none or its chunk is not loaded. */
    int freeSlots(BlockPos container);

    /**
     * The non-empty slots of the container at {@code container}, in slot order, each with its damage; empty when there
     * is none or its chunk is not loaded.
     */
    List<ItemAmount> stacks(BlockPos container);
}
