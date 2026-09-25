package dev.hycolony.core.colony;

import dev.hycolony.core.kernel.BlockPos;

/** A 16x16 claim cell (the size of a Minecraft chunk) so distances match MineColonies exactly. */
public record ClaimCell(int x, int z) {
    public static final int SIZE = 16;

    public static ClaimCell of(BlockPos pos) {
        return new ClaimCell(Math.floorDiv(pos.x(), SIZE), Math.floorDiv(pos.z(), SIZE));
    }
}
