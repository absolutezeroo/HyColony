package dev.hycolony.core.construction.blueprint;

import dev.hycolony.core.kernel.BlockPos;
import dev.hycolony.core.kernel.item.BlockState;
import dev.hycolony.core.kernel.item.Workstation;
import java.util.Objects;
import java.util.Optional;

/**
 * One block of a blueprint, already rotated, relative to the hut block. {@code workstation} is the crafting bench
 * this block is, with the tier the plan gives it; the hut registers it once placed.
 */
public record BlueprintEntry(
        BlockPos offset, BlockState state, boolean hasContainer, Optional<Workstation> workstation) {
    public BlueprintEntry {
        Objects.requireNonNull(workstation, "workstation");
    }

    /** A block that is no crafting bench. */
    public BlueprintEntry(BlockPos offset, BlockState state, boolean hasContainer) {
        this(offset, state, hasContainer, Optional.empty());
    }
}
