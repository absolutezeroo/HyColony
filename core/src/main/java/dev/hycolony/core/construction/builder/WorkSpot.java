package dev.hycolony.core.construction.builder;

import dev.hycolony.core.construction.blueprint.StructurePlan;
import dev.hycolony.core.kernel.BlockPos;
import dev.hycolony.core.kernel.item.BlockKind;
import dev.hycolony.core.kernel.item.BlockState;
import dev.hycolony.core.kernel.port.ItemCatalog;
import dev.hycolony.core.kernel.port.WorldBlocks;

/**
 * Where the builder stands to work on a block: MC EntityAIStructureBuilder.walkToConstructionSite's {@code workFrom},
 * found by PathJobMoveCloseToXNearY(block, site, 4). Without a path search, a spot is tried 2 to 4 blocks from the
 * block, outward from the site first (the builder then faces the structure), then to the sides, then inward. Its feet
 * stand on the ground found in that column, and neither its feet nor its head is a cell the plan will fill.
 *
 * <p>Deviation from MC: no path search (Hytale's nav owns paths), so the spot is picked from the world and the plan.
 */
final class WorkSpot {
    /** walkToConstructionSite: a new spot once BlockPosUtil.getDistance2D(worker, block), |dx| + |dz|, exceeds 5. */
    static final int REACH = 5;

    private static final int MIN_OUT = 2;
    /** PathJobMoveCloseToXNearY's range. */
    private static final int MAX_OUT = 4;
    /** How far a column is searched for ground, down (or up out of the ground) from the block's level + 1. */
    private static final int GROUND_SCAN = 16;

    private final WorldBlocks blocks;
    private final ItemCatalog catalog;

    WorkSpot(WorldBlocks blocks, ItemCatalog catalog) {
        this.blocks = blocks;
        this.catalog = catalog;
    }

    static boolean inReach(BlockPos standing, BlockPos block) {
        return Math.abs(standing.x() - block.x()) + Math.abs(standing.z() - block.z()) <= REACH;
    }

    /**
     * The first free spot with ground under it; else the first free one in a column with no ground at all (unloaded
     * or open terrain, neither buried nor in a fluid: never above unfit ground such as lava); else 2 blocks outward and
     * 1 up, which may be over lava (the walk there then ends, blocked or given up by the stuck handler, and the builder
     * works from where it got).
     */
    BlockPos choose(BlockPos block, BlockPos site, StructurePlan plan) {
        int[][] dirs = directions(block, site);
        BlockPos free = null;
        for (int d = MIN_OUT; d <= MAX_OUT; d++) {
            for (int[] dir : dirs) {
                BlockPos top = block.offset(dir[0] * d, 1, dir[1] * d);
                BlockPos feet = ground(top);
                if (feet == null) {
                    if (free == null && standable(top, plan)) {
                        free = top;
                    }
                } else if (fits(feet) && open(plan, feet)) {
                    return feet;
                }
            }
        }
        return free != null ? free : block.offset(dirs[0][0] * MIN_OUT, 1, dirs[0][1] * MIN_OUT);
    }

    /** Neither the feet cell nor the head cell above it is one the plan will fill. */
    private boolean open(StructurePlan plan, BlockPos feet) {
        return !planned(plan, feet) && !planned(plan, feet.offset(0, 1, 0));
    }

    /** Outward (the dominant axis from the site to the block), its two sides, then inward. */
    private static int[][] directions(BlockPos block, BlockPos site) {
        int dx = block.x() - site.x();
        int dz = block.z() - site.z();
        int[] out = Math.abs(dx) >= Math.abs(dz) ? new int[] {dx >= 0 ? 1 : -1, 0} : new int[] {0, dz >= 0 ? 1 : -1};
        return new int[][] {out, {-out[1], out[0]}, {out[1], -out[0]}, {-out[0], -out[1]}};
    }

    /**
     * Feet position on the first solid block below {@code top}, or above it when {@code top} is in the ground; null
     * only when the column has no ground at all. The spot found may not {@link #fits fit} a body: the downward scan
     * also stops at a fluid 2 or more deep (a body does not stand at the bottom of a lake), and a buried column with
     * no fitting spot above returns {@code top} itself.
     */
    private BlockPos ground(BlockPos top) {
        return solid(top) ? groundAbove(top) : groundBelow(top);
    }

    /** The first fitting spot on a solid block above the buried {@code top}, else {@code top} (which never fits). */
    private BlockPos groundAbove(BlockPos top) {
        for (int i = 1; i <= GROUND_SCAN; i++) {
            BlockPos p = top.offset(0, i, 0);
            if (solid(p.offset(0, -1, 0)) && fits(p)) {
                return p;
            }
        }
        return top;
    }

    /** The spot on the first solid block below {@code top}, or the first fluid cell with fluid above it; else null. */
    private BlockPos groundBelow(BlockPos top) {
        for (int i = 0; i <= GROUND_SCAN; i++) {
            BlockPos p = top.offset(0, -i, 0);
            if (fluid(p) && fluid(p.offset(0, 1, 0)) || solid(p.offset(0, -1, 0))) {
                return p;
            }
        }
        return null;
    }

    /**
     * Feet and head cells are not solid, and the feet are dry or wade in 1 block of a harmless fluid (ankle-deep
     * water, never lava).
     */
    private boolean fits(BlockPos feet) {
        BlockPos head = feet.offset(0, 1, 0);
        if (solid(feet) || solid(head)) {
            return false;
        }
        return !fluid(feet) || !fluid(head) && !harmful(feet);
    }

    /** Neither the feet nor the head cell is solid, and the feet are not in a fluid. */
    private boolean standable(BlockPos feet) {
        return !solid(feet) && !solid(feet.offset(0, 1, 0)) && !fluid(feet);
    }

    /** Standable, and neither cell is one the plan will fill. */
    private boolean standable(BlockPos feet, StructurePlan plan) {
        return standable(feet) && open(plan, feet);
    }

    private boolean fluid(BlockPos p) {
        BlockState s = blocks.get(p).orElse(null);
        return s != null && catalog.kind(s.key()) == BlockKind.FLUID;
    }

    private boolean harmful(BlockPos p) {
        BlockState s = blocks.get(p).orElse(null);
        return s != null && catalog.isHarmful(s.key());
    }

    private boolean solid(BlockPos p) {
        BlockState s = blocks.get(p).orElse(null);
        return s != null && catalog.kind(s.key()) == BlockKind.SOLID;
    }

    private boolean planned(StructurePlan plan, BlockPos p) {
        BlockState s = plan.stateAt(p);
        return s != null && catalog.kind(s.key()) != BlockKind.AIR;
    }
}
