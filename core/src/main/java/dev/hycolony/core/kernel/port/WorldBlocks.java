package dev.hycolony.core.kernel.port;

import dev.hycolony.core.kernel.BlockPos;
import dev.hycolony.core.kernel.item.BlockState;
import dev.hycolony.core.kernel.item.ItemAmount;
import java.util.List;
import java.util.Optional;

public interface WorldBlocks {
    boolean isLoaded(BlockPos pos);

    /** Empty if the chunk is not loaded. */
    Optional<BlockState> get(BlockPos pos);

    boolean place(BlockPos pos, BlockState state, boolean withContainer);

    /** Drops, including container contents. Empty list if the block is air or unloaded. */
    List<ItemAmount> breakBlock(BlockPos pos);

    /** Drops {@code items} on the ground at {@code pos}, like a broken block's drops; nothing if unloaded. */
    void drop(BlockPos pos, List<ItemAmount> items);
}
