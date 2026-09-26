package dev.hycolony.core.kernel;

public record Vec3(double x, double y, double z) {
    public BlockPos toBlockPos() {
        return new BlockPos((int) Math.floor(x), (int) Math.floor(y), (int) Math.floor(z));
    }

    /** The middle of the block's floor: where a body standing in {@code p} has its feet. */
    public static Vec3 center(BlockPos p) {
        return new Vec3(p.x() + 0.5, p.y(), p.z() + 0.5);
    }

    /** The middle of the block itself: what a worker looks at. */
    public static Vec3 middle(BlockPos p) {
        return new Vec3(p.x() + 0.5, p.y() + 0.5, p.z() + 0.5);
    }

    public double distance(Vec3 o) {
        double dx = x - o.x, dy = y - o.y, dz = z - o.z;
        return Math.sqrt(dx * dx + dy * dy + dz * dz);
    }
}
