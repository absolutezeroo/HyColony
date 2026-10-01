package dev.hydomum.core.connect;

import java.util.EnumSet;
import java.util.Set;

/**
 * A wall's tall sides and raised post from the block above (MC WallBlock.updateShape, makeWallState,
 * shouldRaisePost; tests in sixteenths of a block).
 */
public final class WallTop {
    /** MC WallBlock.POST_TEST: the middle of the block. */
    static final Footprint.Rect POST = sixteenths(7, 7, 9, 9);

    private WallTop() {}

    /**
     * The look of a wall joined on joined, under a block whose bottom face is above (aboveWallPost: it is a wall with
     * its post raised): a joined side is tall when above covers its band; the post rises under a raised wall post,
     * when an arm lacks its opposite (alone, an end, a corner, a T), never on a line of two tall opposite arms, else
     * when above covers the middle.
     */
    public static WallLook of(Set<Side> joined, Footprint above, boolean aboveWallPost) {
        Set<Side> tall = EnumSet.noneOf(Side.class);
        for (Side side : joined) {
            if (above.covers(sideTest(side))) {
                tall.add(side);
            }
        }
        return new WallLook(tall, raisesPost(joined, tall, above, aboveWallPost));
    }

    private static boolean raisesPost(Set<Side> joined, Set<Side> tall, Footprint above, boolean aboveWallPost) {
        boolean unpaired = joined.contains(Side.NORTH) != joined.contains(Side.SOUTH)
                || joined.contains(Side.EAST) != joined.contains(Side.WEST);
        if (aboveWallPost || joined.isEmpty() || unpaired) {
            return true;
        }
        boolean tallLine = (tall.contains(Side.NORTH) && tall.contains(Side.SOUTH))
                || (tall.contains(Side.EAST) && tall.contains(Side.WEST));
        // Deviation from MC: no WALL_POST_OVERRIDE tag; a block above counts by its collision only (a Hytale torch,
        // sign or banner may collide or not).
        return !tallLine && above.covers(POST);
    }

    /** MC WallBlock.NORTH_TEST, SOUTH_TEST...: the band from the middle to side. */
    private static Footprint.Rect sideTest(Side side) {
        return switch (side) {
            case NORTH -> sixteenths(7, 0, 9, 9);
            case SOUTH -> sixteenths(7, 7, 9, 16);
            case WEST -> sixteenths(0, 7, 9, 9);
            case EAST -> sixteenths(7, 7, 16, 9);
        };
    }

    private static Footprint.Rect sixteenths(int minX, int minZ, int maxX, int maxZ) {
        return new Footprint.Rect(minX / 16.0, minZ / 16.0, maxX / 16.0, maxZ / 16.0);
    }
}
