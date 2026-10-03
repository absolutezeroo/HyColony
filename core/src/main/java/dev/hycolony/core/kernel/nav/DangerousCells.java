package dev.hycolony.core.kernel.nav;

import dev.hycolony.core.kernel.BlockPos;
import dev.hycolony.core.kernel.catalog.BlockCatalog;
import dev.hycolony.core.kernel.item.BlockState;
import dev.hycolony.core.kernel.port.WorldBlocks;

/**
 * Cells a citizen must never be sent to: MC PathfindingUtils.isDangerous (fire, campfire, magma, lava...) as
 * SurfaceType.getSurfaceType and AbstractPathJob.isPassable apply it, where a dangerous block is not passable and
 * neither is the cell above one. Danger is {@link BlockCatalog#isHarmful}.
 *
 * <p>Walk targets are kept off these cells, and {@link SafeRoute} routes walks around them.
 *
 * <p>Deviation from MC: a spot where a citizen stands still is also kept {@link #near 1 block} away from them. MC only
 * refuses the dangerous cell itself, but Hytale's steering and body push make a citizen stopped at the edge touch
 * the block, and a low one (a campfire, a 0.3 high brazier) is walked over as a step.
 */
public final class DangerousCells {
    private final WorldBlocks blocks;
    private final BlockCatalog catalog;

    public DangerousCells(WorldBlocks blocks, BlockCatalog catalog) {
        this.blocks = blocks;
        this.catalog = catalog;
    }

    /**
     * Whether any cell from {@code center.y - halfHeight} to {@code center.y + halfHeight} is harmful. With the feet
     * as center and 1, that is the floor, the feet and the head. Unloaded cells count as safe.
     */
    public boolean inColumn(BlockPos center, int halfHeight) {
        for (int dy = -halfHeight; dy <= halfHeight; dy++) {
            BlockState s = blocks.get(center.offset(0, dy, 0)).orElse(null);
            if (s != null && catalog.isHarmful(s.key())) {
                return true;
            }
        }
        return false;
    }

    /**
     * Whether {@link #inColumn} holds for the column of {@code center} or any of its 8 neighbours, diagonals included:
     * a body standing at {@code center} could touch a harmful block.
     */
    public boolean near(BlockPos center, int halfHeight) {
        for (int dx = -1; dx <= 1; dx++) {
            for (int dz = -1; dz <= 1; dz++) {
                if (inColumn(center.offset(dx, 0, dz), halfHeight)) {
                    return true;
                }
            }
        }
        return false;
    }
}
