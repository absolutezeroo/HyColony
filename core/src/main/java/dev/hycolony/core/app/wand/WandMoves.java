package dev.hycolony.core.app.wand;

import dev.hycolony.core.app.wand.WandActions.Dir;
import dev.hycolony.core.kernel.BlockPos;

/**
 * Pure geometry for moving and rotating a build tool anchor (ST AbstractBlueprintManipulationWindow, l.525-590):
 * forward/back/left/right move one block relative to the player's facing, up/down move on Y only, and rotation
 * is by quarter turns.
 */
final class WandMoves {
    private WandMoves() {}

    /**
     * Moves {@code anchor} one block in {@code dir}, relative to {@code facing} (0 = north, 1 = east, 2 = south,
     * 3 = west, clockwise from north, as {@link dev.hycolony.core.kernel.port.PlayerDirectory#facing}). Left is
     * counterclockwise of forward, right is clockwise; up and down move on Y only, ignoring facing.
     */
    static BlockPos move(BlockPos anchor, Dir dir, int facing) {
        return switch (dir) {
            case UP -> anchor.offset(0, 1, 0);
            case DOWN -> anchor.offset(0, -1, 0);
            case FORWARD -> step(anchor, facing);
            case BACK -> step(anchor, (facing + 2) % 4);
            case LEFT -> step(anchor, rotate(facing, false));
            case RIGHT -> step(anchor, rotate(facing, true));
        };
    }

    /** Rotates a quarter-turn direction (0-3) by one quarter turn, wrapping at the ends. */
    static int rotate(int rotation, boolean clockwise) {
        return clockwise ? (rotation + 1) % 4 : (rotation + 3) % 4;
    }

    private static BlockPos step(BlockPos anchor, int facing) {
        return anchor.offset(dx(facing), 0, dz(facing));
    }

    private static int dx(int facing) {
        return switch (facing) {
            case 1 -> 1;
            case 3 -> -1;
            default -> 0;
        };
    }

    private static int dz(int facing) {
        return switch (facing) {
            case 0 -> -1;
            case 2 -> 1;
            default -> 0;
        };
    }
}
