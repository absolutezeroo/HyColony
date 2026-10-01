package dev.hydomum.core.connect;

import java.util.EnumSet;
import java.util.Set;

/**
 * A horizontal side of a block, in the order a Hytale yaw step turns them (Rotation.rotateY, Ninety: west to south):
 * one step turns each side into the next.
 */
public enum Side {
    NORTH,
    WEST,
    SOUTH,
    EAST;

    private static final Side[] VALUES = values();

    /** This side turned by yaw quarter turns (0 to 3). */
    Side turned(int yaw) {
        return VALUES[(ordinal() + yaw) % VALUES.length];
    }

    /** Every side of sides turned by yaw quarter turns. */
    static Set<Side> turned(Set<Side> sides, int yaw) {
        Set<Side> result = EnumSet.noneOf(Side.class);
        for (Side side : sides) {
            result.add(side.turned(yaw));
        }
        return result;
    }
}
