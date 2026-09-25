package dev.hycolony.core.kernel.port;

import dev.hycolony.core.kernel.BlockPos;
import dev.hycolony.core.kernel.item.BlockState;
import dev.hycolony.core.kernel.item.ItemAmount;
import dev.hycolony.core.kernel.item.ItemKey;
import java.util.List;
import java.util.Optional;

public interface WorldBlocks {
    boolean isLoaded(BlockPos pos);

    /** Empty if the chunk is not loaded. */
    Optional<BlockState> get(BlockPos pos);

    boolean place(BlockPos pos, BlockState state, boolean withContainer);

    /** Drops, including container contents. Empty list if the block is air or unloaded. */
    List<ItemAmount> breakBlock(BlockPos pos);

    /** Wears the tool held by that citizen body by 1. No-op if unknown. */
    void damageTool(BodyId body, ItemKey tool);
}
