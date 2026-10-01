package dev.hydomum.core.connect;

import java.util.EnumSet;
import java.util.Map;
import java.util.Set;

/**
 * Which neighbours a fence, wall or bars block joins (MC FenceBlock.connectsTo, WallBlock.connectsTo,
 * IronBarsBlock.attachsTo, which DO's blocks inherit).
 *
 * <p>Minecraft never joins a full face of its exceptions (Block.isExceptionForConnection: leaves, barrier, pumpkins,
 * melon, shulker boxes); none of their Hytale counterparts has a full face (leaves and pumpkins are models, the
 * barrier is empty), so the full face alone decides here.
 */
public final class Connections {
    private Connections() {}

    /**
     * Whether joiner reaches an arm to neighbour, lying on side of it: a full face, a gate's side (not for bars), and
     * its own family: wooden fences, other fences (MC isSameFence), walls with walls and bars.
     */
    public static boolean joins(Joiner joiner, Side side, Neighbour neighbour) {
        NeighbourKind kind = neighbour.kind();
        if (neighbour.fullFace()) {
            return true;
        }
        if (kind == NeighbourKind.GATE) {
            return joiner != Joiner.PANE && gateSideFaces(side, neighbour.yaw());
        }
        return switch (joiner) {
            case WOODEN_FENCE -> kind == NeighbourKind.WOODEN_FENCE;
            case FENCE -> kind == NeighbourKind.FENCE;
            case WALL, PANE -> kind == NeighbourKind.WALL || kind == NeighbourKind.PANE;
        };
    }

    /** The sides of around (a side missing is no neighbour) whose neighbour joiner joins. */
    public static Set<Side> joinedSides(Joiner joiner, Map<Side, Neighbour> around) {
        Set<Side> joined = EnumSet.noneOf(Side.class);
        around.forEach((side, neighbour) -> {
            if (joins(joiner, side, neighbour)) {
                joined.add(side);
            }
        });
        return joined;
    }

    /**
     * Whether a gate at yaw, lying on side of us, shows us one of its sides (MC FenceGateBlock.connectsToDirection):
     * its sides face east and west at yaw 0, as the vanilla fence template's Gate shape.
     */
    private static boolean gateSideFaces(Side side, int yaw) {
        boolean alongX = side == Side.EAST || side == Side.WEST;
        return alongX == (yaw % 2 == 0);
    }
}
