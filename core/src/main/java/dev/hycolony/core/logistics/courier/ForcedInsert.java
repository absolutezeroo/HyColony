package dev.hycolony.core.logistics.courier;

import dev.hycolony.core.kernel.BlockPos;
import dev.hycolony.core.kernel.item.ItemAmount;
import dev.hycolony.core.kernel.item.ItemKey;
import dev.hycolony.core.kernel.port.ContainerAccess;
import java.util.List;
import java.util.Optional;
import java.util.function.Predicate;

/**
 * MC InventoryUtils.forceItemStackToItemHandler: an insert that makes room by swapping out an unneeded stack.
 * Deviation from MC: the container port extracts by item, not by slot, so the swap may take the item from another slot
 * of the same container; a swap that made no room is undone.
 */
final class ForcedInsert {
    private ForcedInsert() {}

    /**
     * Inserts {@code stack} into {@code containers}; if some does not fit, puts the rest in place of the first stack
     * that {@code keep} does not protect. Returns null when all went in, the swapped-out stack after a swap, or the
     * rest when no swap made room (the whole {@code stack} when nothing fitted), like the port's insert.
     */
    static ItemAmount insert(
            ContainerAccess access, List<BlockPos> containers, ItemAmount stack, Predicate<ItemKey> keep) {
        ItemAmount rest = access.insert(containers, stack);
        if (rest == null) {
            return null;
        }
        for (BlockPos container : containers) {
            for (ItemAmount local : access.stacks(container)) {
                if (!keep.test(local.item())) {
                    Optional<ItemAmount> swapped = swap(access, List.of(container), local, rest);
                    if (swapped.isPresent()) {
                        return swapped.get();
                    }
                }
            }
        }
        return rest;
    }

    /** Takes {@code local} out and puts {@code rest} in; undoes both and returns empty when {@code rest} did not fit. */
    private static Optional<ItemAmount> swap(
            ContainerAccess access, List<BlockPos> container, ItemAmount local, ItemAmount rest) {
        int removed = access.extract(container, local.item(), local.count());
        if (removed <= 0) {
            return Optional.empty();
        }
        ItemAmount left = access.insert(container, rest);
        if (left == null) {
            return Optional.of(local.withCount(removed));
        }
        int placed = rest.count() - left.count();
        if (placed > 0) {
            access.extract(container, rest.item(), placed);
        }
        access.insert(container, local.withCount(removed));
        return Optional.empty();
    }
}
