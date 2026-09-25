package dev.hycolony.core.testing;

import dev.hycolony.core.kernel.BlockPos;
import dev.hycolony.core.kernel.item.BlockState;
import dev.hycolony.core.kernel.item.ItemAmount;
import dev.hycolony.core.kernel.port.WorldBlocks;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

public final class FakeWorldBlocks implements WorldBlocks {
    public final Map<BlockPos, BlockState> blocks = new LinkedHashMap<>();
    /** Drops returned by breakBlock for a given position, set up by the test. */
    public final Map<BlockPos, List<ItemAmount>> drops = new LinkedHashMap<>();
    public boolean loaded = true;
    /** Every successful place(), in call order. */
    public final List<BlockPos> placed = new ArrayList<>();

    @Override public boolean isLoaded(BlockPos pos) { return loaded; }
    @Override public Optional<BlockState> get(BlockPos pos) { return Optional.ofNullable(blocks.get(pos)); }

    @Override
    public boolean place(BlockPos pos, BlockState state, boolean withContainer) {
        blocks.put(pos, state);
        placed.add(pos);
        return true;
    }

    @Override
    public List<ItemAmount> breakBlock(BlockPos pos) {
        BlockState removed = blocks.remove(pos);
        if (removed == null) {
            return List.of();
        }
        return drops.getOrDefault(pos, List.of());
    }
}
