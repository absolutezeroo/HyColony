package dev.hyangler.core.context;

import dev.hyangler.core.port.BlockProbe;

/**
 * Vanilla's open water (FishingHook.calculateOpenWater): the four layers from one below the bobber to two above, each
 * a 5 x 5 area all of still water or all of air, water layers below air layers, the first one under water.
 */
public final class OpenWater {
    private enum Layer {
        INSIDE_WATER,
        ABOVE_WATER,
        INVALID
    }

    private OpenWater() {}

    /** Whether the bobber at (x, y, z) floats in open water. */
    public static boolean test(BlockProbe probe, int x, int y, int z) {
        Layer previous = Layer.INVALID;
        for (int dy = -1; dy <= 2; dy++) {
            Layer layer = layer(probe, x, y + dy, z);
            if (layer == Layer.INVALID
                    || (layer == Layer.ABOVE_WATER && previous == Layer.INVALID)
                    || (layer == Layer.INSIDE_WATER && previous == Layer.ABOVE_WATER)) {
                return false;
            }
            previous = layer;
        }
        return true;
    }

    /** The 5 x 5 layer at height y round (x, z): all still water, all air, or anything else. */
    private static Layer layer(BlockProbe probe, int x, int y, int z) {
        Layer layer = Layer.INVALID;
        boolean first = true;
        for (int dx = -2; dx <= 2; dx++) {
            for (int dz = -2; dz <= 2; dz++) {
                Layer here = switch (probe.kind(x + dx, y, z + dz)) {
                    case WATER_SOURCE -> Layer.INSIDE_WATER;
                    case AIR -> Layer.ABOVE_WATER;
                    default -> Layer.INVALID;
                };
                if (here == Layer.INVALID || (!first && layer != here)) {
                    return Layer.INVALID;
                }
                layer = here;
                first = false;
            }
        }
        return layer;
    }
}
