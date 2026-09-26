package dev.hycolony.core.construction.blueprint;

import dev.hycolony.core.kernel.BlockPos;
import dev.hycolony.core.kernel.item.BlockState;

/** One block of a blueprint, already rotated, relative to the hut block. */
public record BlueprintEntry(BlockPos offset, BlockState state, boolean hasContainer) {}
