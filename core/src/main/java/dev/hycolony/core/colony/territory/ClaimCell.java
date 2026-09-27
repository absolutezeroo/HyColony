package dev.hycolony.core.colony.territory;

import dev.hycolony.core.kernel.BlockPos;
import java.util.function.Predicate;

/** A 16x16 claim cell (the size of a Minecraft chunk) so distances match MineColonies exactly. */
public record ClaimCell(int x, int z) {
    public static final int SIZE = 16;

    public static ClaimCell of(BlockPos pos) {
        return new ClaimCell(Math.floorDiv(pos.x(), SIZE), Math.floorDiv(pos.z(), SIZE));
    }

    /**
     * MC WorkManager.isWorkOrderWithinColony: whether {@code owned} holds for every claim cell of the x/z rectangle
     * between corners {@code a} and {@code b} (tested at each cell's corner, at {@code a}'s height).
     */
    public static boolean allOwned(BlockPos a, BlockPos b, Predicate<BlockPos> owned) {
        ClaimCell ca = of(a);
        ClaimCell cb = of(b);
        for (int cx = Math.min(ca.x(), cb.x()); cx <= Math.max(ca.x(), cb.x()); cx++) {
            for (int cz = Math.min(ca.z(), cb.z()); cz <= Math.max(ca.z(), cb.z()); cz++) {
                if (!owned.test(new BlockPos(cx * SIZE, a.y(), cz * SIZE))) {
                    return false;
                }
            }
        }
        return true;
    }
}
