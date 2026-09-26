package dev.hycolony.core.kernel.nav;

import dev.hycolony.core.kernel.BlockPos;
import dev.hycolony.core.kernel.item.BlockState;
import dev.hycolony.core.kernel.port.ItemCatalog;
import dev.hycolony.core.kernel.port.WorldBlocks;

/**
 * Cells a citizen must never be sent to: MC PathfindingUtils.isDangerous (fire, campfire, magma, lava...) as
 * SurfaceType.getSurfaceType and AbstractPathJob.isPassable apply it, where a dangerous block is not passable and
 * neither is the cell above one. Danger is {@link ItemCatalog#isHarmful}.
 *
 * <p>Walk targets are kept off these cells, and {@link SafeRoute} routes walks around them.
 */
public final class DangerousCells {
    private final WorldBlocks blocks;
    private final ItemCatalog catalog;

    public DangerousCells(WorldBlocks blocks, ItemCatalog catalog) {
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
}
