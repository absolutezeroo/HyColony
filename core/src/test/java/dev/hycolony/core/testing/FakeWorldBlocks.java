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
import java.util.function.Consumer;

public final class FakeWorldBlocks implements WorldBlocks {
    public final Map<BlockPos, BlockState> blocks = new LinkedHashMap<>();
    /** Drops returned by breakBlock for a given position, set up by the test. */
    public final Map<BlockPos, List<ItemAmount>> drops = new LinkedHashMap<>();

    /** Items dropped on the ground by drop(), by position, in call order. */
    public final Map<BlockPos, List<ItemAmount>> dropped = new LinkedHashMap<>();

    public boolean loaded = true;
    /** When true, place() fails and changes nothing (a Hytale placement refused by a hitbox or unloaded chunk). */
    public boolean refusePlace;
    /** How many times get() was called. */
    public int reads;
    /** Every successful place(), in call order. */
    public final List<BlockPos> placed = new ArrayList<>();
    /** Every position changed through placeQuietly() or breakQuietly(), in call order. */
    public final List<BlockPos> quiet = new ArrayList<>();
    /** Runs before every place() and breakBlock(), with the position (ordering checks). */
    public Consumer<BlockPos> beforeChange = p -> {};
    /** Tiers given by setBenchTier(), by position; a block placed or broken there drops its entry. */
    public final Map<BlockPos, Integer> benchTiers = new LinkedHashMap<>();
    /** When true, setBenchTier() fails and changes nothing (the block there is no bench for Hytale). */
    public boolean refuseBenchTier;

    @Override
    public boolean isLoaded(BlockPos pos) {
        return loaded;
    }

    @Override
    public Optional<BlockState> get(BlockPos pos) {
        reads++;
        return Optional.ofNullable(blocks.get(pos));
    }

    @Override
    public boolean place(BlockPos pos, BlockState state, boolean withContainer) {
        if (refusePlace) {
            return false;
        }
        beforeChange.accept(pos);
        blocks.put(pos, state);
        benchTiers.remove(pos);
        placed.add(pos);
        return true;
    }

    @Override
    public List<ItemAmount> breakBlock(BlockPos pos) {
        beforeChange.accept(pos);
        benchTiers.remove(pos);
        BlockState removed = blocks.remove(pos);
        if (removed == null) {
            return List.of();
        }
        return drops.getOrDefault(pos, List.of());
    }

    @Override
    public boolean placeQuietly(BlockPos pos, BlockState state, boolean withContainer) {
        if (refusePlace) {
            return false;
        }
        quiet.add(pos);
        return place(pos, state, withContainer);
    }

    @Override
    public List<ItemAmount> breakQuietly(BlockPos pos) {
        quiet.add(pos);
        return breakBlock(pos);
    }

    /** False when refused, unloaded or with no block at {@code pos}; this fake takes any block for a bench. */
    @Override
    public boolean setBenchTier(BlockPos pos, int tier) {
        if (refuseBenchTier || !loaded || !blocks.containsKey(pos)) {
            return false;
        }
        benchTiers.put(pos, tier);
        return true;
    }

    @Override
    public void drop(BlockPos pos, List<ItemAmount> items) {
        dropped.computeIfAbsent(pos, p -> new ArrayList<>()).addAll(items);
    }
}
