package dev.hycolony.core.colony;

import dev.hycolony.core.building.Building;
import dev.hycolony.core.kernel.BlockPos;
import dev.hycolony.core.kernel.item.BlockKind;
import dev.hycolony.core.kernel.item.BlockState;
import dev.hycolony.core.kernel.nav.BodyWalker;
import dev.hycolony.core.kernel.nav.DangerousCells;
import org.jspecify.annotations.Nullable;

/**
 * One worker's walks to a block it cannot stand in (a hut block, a bench, a rack): MC EntityNavigationUtils, whose
 * path job (PathJobMoveCloseToXNearY, PathJobMoveToLocation) ends on a cell beside the block, at its level, over a
 * walkable floor; in a building, the one nearest the centre of the building's corners (getEndNodeScore). A citizen
 * thus stands inside by its hut, not on the roof above it. The cell is looked for again at each new walk.
 *
 * <p>Deviation from MC: no path search (Hytale's nav owns paths), so the cell is picked from the world, not among
 * reachable ones, and kept 1 block from danger like every standing spot ({@link DangerousCells}); ties go north, east,
 * south, west. Without a free cell, the block itself is the target, unchecked (never teleported onto).
 */
public final class BlockApproach {
    /** MC EntityNavigationUtils.BUILDING_REACH_DIST, also walkToSafePos's reach: blocks from the block. */
    private static final int BUILDING_REACH = 4;
    /** MC EntityNavigationUtils.WOKR_IN_BUILDING_DIST (walkToWorkPos, walkToTaggedWorkPos): blocks from the block. */
    public static final int WORK_IN_BUILDING_REACH = 7;
    /** MC PathJobMoveCloseToXNearY.getEndNodeScore: added for a cell in water. */
    private static final int SWIM_PENALTY = 50;
    /** The four sides of the block, in tie order. */
    private static final int[][] SIDES = {{0, -1}, {1, 0}, {0, 1}, {-1, 0}};

    /** The cell to walk to; {@code verified} is false only for the block itself. */
    private record Spot(BlockPos pos, boolean verified) {}

    private final GamePorts ports;
    private final BodyWalker walker;
    private final DangerousCells danger;
    private @Nullable BlockPos forBlock;
    private int forWalk = -1;
    private @Nullable Spot spot;

    /** The walks of the body {@code walker} moves: a cell found is only valid for that walker's walks. */
    public BlockApproach(GamePorts ports, BodyWalker walker) {
        this.ports = ports;
        this.walker = walker;
        this.danger = new DangerousCells(ports.blocks(), ports.blockCatalog());
    }

    /** MC walkToBuilding: to the hut block of {@code building}; true once there. */
    public boolean walkToBuilding(Building building) {
        return walkToPosInBuilding(building.position(), building, BUILDING_REACH);
    }

    /** MC walkToPosInBuilding: to {@code pos}, from the side of {@code building}'s centre; true once within reach. */
    public boolean walkToPosInBuilding(BlockPos pos, Building building, int reach) {
        return walk(pos, building, reach);
    }

    /** MC walkToSafePos: to {@code pos}, outside any building; true once within {@link #BUILDING_REACH}. */
    public boolean walkToSafePos(BlockPos pos) {
        return walk(pos, null, BUILDING_REACH);
    }

    /** Forgets its walker's walk ({@link BodyWalker#forget}) and the cell chosen, looked for again at the next walk. */
    public void forget() {
        walker.forget();
        spot = null;
    }

    /**
     * Walks to the cell beside {@code block} (see {@link BodyWalker#walkCloseTo}); the cell is chosen again when the
     * block changed, when the walker walked elsewhere since, or while none was found.
     */
    private boolean walk(BlockPos block, @Nullable Building building, int reach) {
        Spot s = spot;
        if (s == null || !s.verified() || !block.equals(forBlock) || walker.walks() != forWalk) {
            s = choose(block, building);
            spot = s;
            forBlock = block;
        }
        boolean arrived = walker.walkCloseTo(s.pos(), block, reach, s.verified());
        forWalk = walker.walks();
        return arrived;
    }

    /** The standable side of {@code block} with MC's lowest end score; the block itself, unchecked, without one. */
    private Spot choose(BlockPos block, @Nullable Building building) {
        BlockPos best = null;
        int bestScore = 0;
        BlockPos nearby = null;
        for (int[] side : SIDES) {
            BlockPos feet = block.offset(side[0], 0, side[1]);
            if (!standable(feet)) {
                continue;
            }
            if (nearby == null) {
                nearby = building == null ? block : centre(building);
            }
            int score = manhattan(feet, nearby) + (kind(feet) == BlockKind.FLUID ? SWIM_PENALTY : 0);
            if (best == null || score < bestScore) {
                best = feet;
                bestScore = score;
            }
        }
        return best == null ? new Spot(block, false) : new Spot(best, true);
    }

    /**
     * MC walkToPosInBuilding: the centre of the building's corners at the hut's level, from the plan of its level (at
     * least 1, MC AbstractSchematicProvider); the hut without a plan.
     */
    private BlockPos centre(Building building) {
        HutFootprint.Box box = HutFootprint.of(ports, building);
        return new BlockPos(
                (box.min().x() + box.max().x()) / 2,
                building.position().y(),
                (box.min().z() + box.max().z()) / 2);
    }

    /** Feet and head cells passable, a floor to stand on (MC SurfaceType.WALKABLE), no danger near. */
    private boolean standable(BlockPos feet) {
        return !blocks(feet) && !blocks(feet.offset(0, 1, 0)) && blocks(feet.offset(0, -1, 0)) && !danger.near(feet, 1);
    }

    /** A solid block, or one the builder cannot break (hut blocks, furniture): no body enters or falls through it. */
    private boolean blocks(BlockPos p) {
        BlockKind k = kind(p);
        return k == BlockKind.SOLID || k == BlockKind.UNBREAKABLE;
    }

    /** The kind of the block at {@code p}; null where the world has none (unloaded). */
    private @Nullable BlockKind kind(BlockPos p) {
        BlockState s = ports.blocks().get(p).orElse(null);
        return s == null ? null : ports.blockCatalog().kind(s.key());
    }

    private static int manhattan(BlockPos a, BlockPos b) {
        return Math.abs(a.x() - b.x()) + Math.abs(a.y() - b.y()) + Math.abs(a.z() - b.z());
    }
}
