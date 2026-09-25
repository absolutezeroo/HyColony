package dev.hycolony.core.construction;

import dev.hycolony.core.kernel.BlockPos;
import java.util.List;

/** Entries (already rotated, hut block and filler excluded) plus the bounds, all relative to the hut. */
public record Blueprint(String key, List<BlueprintEntry> entries, BlockPos min, BlockPos max) {}
