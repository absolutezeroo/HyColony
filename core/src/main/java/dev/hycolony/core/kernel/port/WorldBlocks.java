package dev.hycolony.core.kernel.port;

import dev.hycolony.core.kernel.BlockPos;
import dev.hycolony.core.kernel.item.BlockState;
import dev.hycolony.core.kernel.item.ItemAmount;
import java.util.List;
import java.util.Optional;
import java.util.OptionalInt;

public interface WorldBlocks {
    /**
     * False while {@code pos} is in a part of the world that may load later; a cell that can never hold a block
     * (outside the world's height) counts as loaded, so nothing waits for it.
     */
    boolean isLoaded(BlockPos pos);

    /** Empty if the chunk is not loaded. */
    Optional<BlockState> get(BlockPos pos);

    boolean place(BlockPos pos, BlockState state, boolean withContainer);

    /** Drops, including container contents with their damage. Empty list if the block is air or unloaded. */
    List<ItemAmount> breakBlock(BlockPos pos);

    /**
     * {@link #place} without the block's particles and sound: a creative paste changes up to maxOperationsPerTick
     * blocks a tick, and ST sends no effect for them.
     */
    boolean placeQuietly(BlockPos pos, BlockState state, boolean withContainer);

    /** {@link #breakBlock} without the block's particles and sound, for the same reason as {@link #placeQuietly}. */
    List<ItemAmount> breakQuietly(BlockPos pos);

    /** Drops {@code items}, with their damage, at {@code pos} like a broken block's drops; nothing if unloaded. */
    void drop(BlockPos pos, List<ItemAmount> items);

    /**
     * Sets the crafting bench at {@code pos} to {@code tier} (Hytale {@code BenchBlock} tier level and its
     * {@code Tier<N>} block state). False if the chunk is not loaded or the block there is no bench.
     */
    boolean setBenchTier(BlockPos pos, int tier);

    /** The tier of the crafting bench at {@code pos}; empty if the chunk is not loaded or the block is no bench. */
    OptionalInt benchTier(BlockPos pos);
}
