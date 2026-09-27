package dev.hycolony.core.construction.builder;

import dev.hycolony.core.construction.blueprint.StructurePlan;
import dev.hycolony.core.kernel.BlockPos;
import dev.hycolony.core.kernel.item.BlockKind;
import dev.hycolony.core.kernel.item.BlockState;
import dev.hycolony.core.kernel.nav.DangerousCells;
import dev.hycolony.core.kernel.port.ItemCatalog;
import dev.hycolony.core.kernel.port.WorldBlocks;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.jspecify.annotations.Nullable;

/**
 * Where the builder stands to work on a block: MC EntityAIStructureBuilder.walkToConstructionSite's {@code workFrom},
 * found by PathJobMoveCloseToXNearY(block, site, 4). Without a path search, a spot is tried 2 to 4 blocks from the
 * block, outward from the site first (the builder then faces the structure), then to the sides, then inward. Its feet
 * stand on the ground found in that column, neither its feet nor its head is a cell the plan will fill, and none of
 * its floor, feet or head is dangerous ({@link DangerousCells}, MC PathfindingUtils.isDangerous).
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

    /** A chosen spot; {@code verified} is false only for the last-resort spot, which was never checked. */
    record Spot(BlockPos pos, boolean verified) {}

    private final WorldBlocks blocks;
    private final ItemCatalog catalog;
    private final DangerousCells danger;

    WorkSpot(WorldBlocks blocks, ItemCatalog catalog) {
        this.blocks = blocks;
        this.catalog = catalog;
        this.danger = new DangerousCells(blocks, catalog);
    }

    static boolean inReach(BlockPos standing, BlockPos block) {
        return Math.abs(standing.x() - block.x()) + Math.abs(standing.z() - block.z()) <= REACH;
    }

    /**
     * The first free spot with ground under it and no dangerous block within 1 block ({@link DangerousCells#near});
     * else the first free spot with ground under it; else the first free one in a column with no ground at all (unloaded
     * or open terrain, neither buried nor in a fluid: never above unfit ground such as lava); else 2 blocks outward and
     * 1 up, unverified since it may be over lava (the walk there then ends, blocked or given up by the stuck handler,
     * and the builder works from where it got).
     *
     * <p>Deviation from MC: the walker never teleports onto an unverified spot, where MC's PathingStuckHandler
     * teleports regardless; it gives up the walk instead.
     */
    Spot choose(BlockPos block, BlockPos site, StructurePlan plan) {
        int[][] dirs = directions(block, site);
        List<BlockPos> tops = new ArrayList<>();
        for (int d = MIN_OUT; d <= MAX_OUT; d++) {
            for (int[] dir : dirs) {
                tops.add(block.offset(dir[0] * d, 1, dir[1] * d));
            }
        }
        return grounded(tops, plan, true)
                .or(() -> grounded(tops, plan, false))
                .or(() -> tops.stream()
                        .filter(top -> ground(top) == null && standable(top, plan))
                        .findFirst())
                .map(pos -> new Spot(pos, true))
                .orElseGet(() -> new Spot(block.offset(dirs[0][0] * MIN_OUT, 1, dirs[0][1] * MIN_OUT), false));
    }

    /** The first fitting, open spot on the ground of {@code tops}; with {@code clear}, also 1 block from danger. */
    private Optional<BlockPos> grounded(List<BlockPos> tops, StructurePlan plan, boolean clear) {
        for (BlockPos top : tops) {
            BlockPos feet = ground(top);
            if (feet != null && fits(feet) && open(plan, feet) && !(clear && danger.near(feet, 1))) {
                return Optional.of(feet);
            }
        }
        return Optional.empty();
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
    private @Nullable BlockPos ground(BlockPos top) {
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
    private @Nullable BlockPos groundBelow(BlockPos top) {
        for (int i = 0; i <= GROUND_SCAN; i++) {
            BlockPos p = top.offset(0, -i, 0);
            if (fluid(p) && fluid(p.offset(0, 1, 0)) || solid(p.offset(0, -1, 0))) {
                return p;
            }
        }
        return null;
    }

    /**
     * Feet and head cells are not solid, floor, feet and head are not dangerous (fire, a campfire underfoot, lava), and
     * the feet are dry or wade in 1 block of fluid (ankle-deep water).
     */
    private boolean fits(BlockPos feet) {
        BlockPos head = feet.offset(0, 1, 0);
        if (solid(feet) || solid(head) || danger.inColumn(feet, 1)) {
            return false;
        }
        return !fluid(feet) || !fluid(head);
    }

    /** Neither the feet nor the head cell is solid, the feet are not in a fluid, and no cell around is dangerous. */
    private boolean standable(BlockPos feet) {
        return !solid(feet) && !solid(feet.offset(0, 1, 0)) && !fluid(feet) && !danger.inColumn(feet, 1);
    }

    /** Standable, and neither cell is one the plan will fill. */
    private boolean standable(BlockPos feet, StructurePlan plan) {
        return standable(feet) && open(plan, feet);
    }

    private boolean fluid(BlockPos p) {
        BlockState s = blocks.get(p).orElse(null);
        return s != null && catalog.kind(s.key()) == BlockKind.FLUID;
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
