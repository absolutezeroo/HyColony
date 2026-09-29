package dev.hycolony.core.construction.blueprint;

import dev.hycolony.core.kernel.BlockPos;
import java.util.List;

/**
 * The Structurize placeholder cells of a MineColonies blueprint, hut-relative (R: structurize-placeholders § 2):
 * {@code air} is cleared (minecraft:air), {@code fill} gets the builder's fill block unless the world already has a good
 * floor there (blocksolidsubstitution), {@code fluid} gets its fluid unless the world has a source or a solid block
 * there (blockfluidsubstitution). Cells in none of these lists nor in the entries keep the terrain (blocksubstitution).
 */
public record BlueprintMarkers(List<BlockPos> air, List<BlockPos> fill, List<BlueprintEntry> fluid) {
    public BlueprintMarkers {
        air = List.copyOf(air);
        fill = List.copyOf(fill);
        fluid = List.copyOf(fluid);
    }
}
