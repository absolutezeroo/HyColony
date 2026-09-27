package dev.hycolony.core.logistics.courier;

import dev.hycolony.core.kernel.BlockPos;
import dev.hycolony.core.kernel.item.ItemAmount;
import dev.hycolony.core.kernel.item.ItemKey;
import dev.hycolony.core.kernel.port.ContainerAccess;
import java.util.List;
import java.util.Optional;
import java.util.function.Predicate;
import org.jspecify.annotations.Nullable;

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
    static @Nullable ItemAmount insert(
            CourierContext ctx, List<BlockPos> containers, ItemAmount stack, Predicate<ItemKey> keep) {
        ItemAmount rest = ctx.containers().insert(containers, stack);
        if (rest == null) {
            return null;
        }
        for (BlockPos container : containers) {
            for (ItemAmount local : ctx.containers().stacks(container)) {
                if (!keep.test(local.item())) {
                    Optional<ItemAmount> swapped = swap(ctx, List.of(container), local, rest);
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
            CourierContext ctx, List<BlockPos> container, ItemAmount local, ItemAmount rest) {
        ContainerAccess access = ctx.containers();
        List<ItemAmount> removed = access.extractStacks(container, local.item(), local.count());
        if (removed.isEmpty()) {
            return Optional.empty();
        }
        ItemAmount left = access.insert(container, rest);
        if (left == null) {
            // A tool stacks to 1, so what one swap takes out of a slot is one stack: its damage goes with it.
            int count = removed.stream().mapToInt(ItemAmount::count).sum();
            return Optional.of(removed.getFirst().withCount(count));
        }
        int placed = rest.count() - left.count();
        if (placed > 0) {
            access.extract(container, rest.item(), placed);
        }
        removed.forEach(r -> ctx.putBack(container, r));
        return Optional.empty();
    }
}
