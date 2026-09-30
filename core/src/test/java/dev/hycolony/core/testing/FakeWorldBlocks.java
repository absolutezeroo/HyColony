package dev.hycolony.core.testing;

import dev.hycolony.core.kernel.BlockPos;
import dev.hycolony.core.kernel.item.BlockState;
import dev.hycolony.core.kernel.item.ItemAmount;
import dev.hycolony.core.kernel.port.WorldBlocks;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Consumer;

public final class FakeWorldBlocks implements WorldBlocks {
    public final Map<BlockPos, BlockState> blocks = new LinkedHashMap<>();
    /** Drops returned by breakBlock for a given position, set up by the test. */
    public final Map<BlockPos, List<ItemAmount>> drops = new LinkedHashMap<>();

    /** Items dropped on the ground by drop(), by position, in call order. */
    public final Map<BlockPos, List<ItemAmount>> dropped = new LinkedHashMap<>();

    /** When false, the whole world reads as unloaded: get() is empty, and nothing is placed, broken or dropped. */
    public boolean loaded = true;
    /** Cells read as unloaded even while {@link #loaded}. */
    public final Set<BlockPos> unloaded = new HashSet<>();
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
        return loaded && !unloaded.contains(pos);
    }

    @Override
    public Optional<BlockState> get(BlockPos pos) {
        reads++;
        return isLoaded(pos) ? Optional.ofNullable(blocks.get(pos)) : Optional.empty();
    }

    @Override
    public boolean place(BlockPos pos, BlockState state, boolean withContainer) {
        if (refusePlace || !isLoaded(pos)) {
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
        if (!isLoaded(pos)) {
            return List.of();
        }
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
        if (refusePlace || !isLoaded(pos)) {
            return false;
        }
        quiet.add(pos);
        return place(pos, state, withContainer);
    }

    @Override
    public List<ItemAmount> breakQuietly(BlockPos pos) {
        if (!isLoaded(pos)) {
            return List.of();
        }
        quiet.add(pos);
        return breakBlock(pos);
    }

    /** False when refused, unloaded or with no block at {@code pos}; this fake takes any block for a bench. */
    @Override
    public boolean setBenchTier(BlockPos pos, int tier) {
        if (refuseBenchTier || !isLoaded(pos) || !blocks.containsKey(pos)) {
            return false;
        }
        benchTiers.put(pos, tier);
        return true;
    }

    @Override
    public void drop(BlockPos pos, List<ItemAmount> items) {
        if (!isLoaded(pos)) {
            return;
        }
        dropped.computeIfAbsent(pos, p -> new ArrayList<>()).addAll(items);
    }
}
