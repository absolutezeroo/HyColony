package dev.hycolony.core.construction.blueprint;

import dev.hycolony.core.kernel.BlockPos;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * Entries (already rotated, hut block and filler excluded) plus the bounds, all relative to the hut. {@code markers}
 * present means a MineColonies blueprint (Structurize semantics: absent cells keep the terrain); absent means a Hytale
 * prefab, whose whole box is cleared.
 */
public record Blueprint(
        String key, List<BlueprintEntry> entries, BlockPos min, BlockPos max, Optional<BlueprintMarkers> markers) {
    public Blueprint {
        Objects.requireNonNull(markers, "markers");
    }

    /** A Hytale prefab's blueprint: no markers. */
    public Blueprint(String key, List<BlueprintEntry> entries, BlockPos min, BlockPos max) {
        this(key, entries, min, max, Optional.empty());
    }
}
