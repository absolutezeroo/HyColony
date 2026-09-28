package dev.hycolony.core.crafting.job;

import dev.hycolony.core.kernel.BlockPos;
import dev.hycolony.core.kernel.item.Inventory;
import dev.hycolony.core.kernel.item.ItemAmount;
import dev.hycolony.core.kernel.item.ItemKey;
import dev.hycolony.core.kernel.port.ContainerAccess;
import java.util.List;
import java.util.function.ToIntFunction;
import org.jspecify.annotations.Nullable;

/** Where a recipe run takes its ingredients and puts its outputs (MC IItemHandler): an inventory, or a hut's racks. */
sealed interface RunStock {
    int count(ItemKey item);

    /** Removes up to {@code max} of {@code item}; returns how many were removed. */
    int extract(ItemKey item, int max);

    /** Puts {@code stack} in; returns what did not fit, or null if all of it did. */
    @Nullable
    ItemAmount insert(ItemAmount stack);

    /** The empty slots, for MC's room estimate; never negative, may be huge for an unbounded container. */
    long freeSlots();

    /** A citizen's inventory, whose stacks hold {@code maxStack} of an item. */
    record InventoryStock(Inventory inventory, ToIntFunction<ItemKey> maxStack) implements RunStock {
        @Override
        public int count(ItemKey item) {
            return inventory.count(item);
        }

        @Override
        public int extract(ItemKey item, int max) {
            return inventory.extract(item, max);
        }

        @Override
        public @Nullable ItemAmount insert(ItemAmount stack) {
            return inventory.insert(stack, maxStack);
        }

        @Override
        public long freeSlots() {
            return inventory.freeSlots();
        }
    }

    /** A hut's racks, hut block first, reached through the container port. */
    record RackStock(List<BlockPos> racks, ContainerAccess access) implements RunStock {
        @Override
        public int count(ItemKey item) {
            return access.count(racks, item);
        }

        @Override
        public int extract(ItemKey item, int max) {
            return access.extract(racks, item, max);
        }

        @Override
        public @Nullable ItemAmount insert(ItemAmount stack) {
            return access.insert(racks, stack);
        }

        @Override
        public long freeSlots() {
            long free = 0;
            for (BlockPos rack : racks) {
                free += access.freeSlots(rack);
            }
            return free;
        }
    }
}
