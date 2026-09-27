package dev.hycolony.core.kernel;

/** Integer block position, independent of any game engine type. */
public record BlockPos(int x, int y, int z) {
    public BlockPos offset(int dx, int dy, int dz) {
        return new BlockPos(x + dx, y + dy, z + dz);
    }

    public long distSq(BlockPos o) {
        long dx = (long) x - o.x, dy = (long) y - o.y, dz = (long) z - o.z;
        return dx * dx + dy * dy + dz * dz;
    }
}
