package dev.hydomum.core.connect;

import java.util.List;

/**
 * The bottom face of a block's collision, seen from below: rectangles in blocks, x east and z south from the block's
 * north-west corner (MC VoxelShape.getFaceShape(Direction.DOWN)).
 */
public record Footprint(List<Rect> rects) {
    /** No collision at the bottom: air, a plant, a torch. */
    public static final Footprint NONE = new Footprint(List.of());

    // Covering is tested on a grid of this many cells per block (a sixteenth of a Minecraft pixel).
    private static final int GRID = 256;

    /** A rectangle of the bottom face, in blocks. */
    public record Rect(double minX, double minZ, double maxX, double maxZ) {
        boolean contains(double x, double z) {
            return minX <= x && x <= maxX && minZ <= z && z <= maxZ;
        }
    }

    public Footprint {
        rects = List.copyOf(rects);
    }

    /** Whether the rects fill test entirely (MC Shapes.joinIsNotEmpty(test, face, ONLY_FIRST) is false). */
    public boolean covers(Rect test) {
        int x0 = (int) Math.floor(test.minX() * GRID);
        int x1 = (int) Math.ceil(test.maxX() * GRID);
        int z0 = (int) Math.floor(test.minZ() * GRID);
        int z1 = (int) Math.ceil(test.maxZ() * GRID);
        for (int x = x0; x < x1; x++) {
            for (int z = z0; z < z1; z++) {
                if (!inAny((x + 0.5) / GRID, (z + 0.5) / GRID)) {
                    return false;
                }
            }
        }
        return true;
    }

    private boolean inAny(double x, double z) {
        for (Rect rect : rects) {
            if (rect.contains(x, z)) {
                return true;
            }
        }
        return false;
    }
}
