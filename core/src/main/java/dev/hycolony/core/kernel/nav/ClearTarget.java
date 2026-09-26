package dev.hycolony.core.kernel.nav;

import dev.hycolony.core.kernel.BlockPos;
import dev.hycolony.core.kernel.Vec3;
import dev.hycolony.core.kernel.item.BlockKind;
import dev.hycolony.core.kernel.item.BlockState;
import dev.hycolony.core.kernel.port.ItemCatalog;
import dev.hycolony.core.kernel.port.WorldBlocks;
import java.util.Comparator;
import java.util.stream.IntStream;

/**
 * Moves a walk's end off the edge of a dangerous block (MC PathfindingUtils.isDangerous), so a citizen never stops
 * where it can touch one: see the deviation on {@link DangerousCells}.
 */
final class ClearTarget {
    /** Farthest a target is moved, in blocks: a caller's arrival check (2 to 3 blocks) still sees the walk arrive. */
    static final int RADIUS = 2;
    /** Floor, feet and head around the feet cell, as {@link DangerousCells#inColumn} reads them. */
    private static final int HALF_HEIGHT = 1;
    /** Feet heights tried per column, relative to the target's: level first, then a step up, then down. */
    private static final int[] STEPS = {0, 1, -1};
    /** The (dx, dz) columns within {@link #RADIUS}, nearest first. */
    private static final int[][] OFFSETS = IntStream.rangeClosed(-RADIUS, RADIUS)
            .boxed()
            .flatMap(dx -> IntStream.rangeClosed(-RADIUS, RADIUS).mapToObj(dz -> new int[] {dx, dz}))
            .filter(o -> o[0] * o[0] + o[1] * o[1] <= RADIUS * RADIUS && (o[0] != 0 || o[1] != 0))
            .sorted(Comparator.comparingInt(o -> o[0] * o[0] + o[1] * o[1]))
            .toArray(int[][]::new);

    private final WorldBlocks blocks;
    private final ItemCatalog catalog;
    private final DangerousCells danger;

    ClearTarget(WorldBlocks blocks, ItemCatalog catalog, DangerousCells danger) {
        this.blocks = blocks;
        this.catalog = catalog;
        this.danger = danger;
    }

    /**
     * {@code target} when no dangerous block lies within 1 block of it; else the centre of the nearest standable cell
     * within {@link #RADIUS} blocks, 1 up or down at most, with none within 1 block; else {@code target} unchanged.
     * At most a few hundred block reads, once per walk.
     */
    Vec3 of(Vec3 target) {
        BlockPos feet = target.toBlockPos();
        if (!danger.near(feet, HALF_HEIGHT)) {
            return target;
        }
        for (int[] o : OFFSETS) {
            for (int dy : STEPS) {
                BlockPos cell = feet.offset(o[0], dy, o[1]);
                if (standable(cell) && !danger.near(cell, HALF_HEIGHT)) {
                    return new Vec3(cell.x() + 0.5, target.y() + dy, cell.z() + 0.5);
                }
            }
        }
        return target;
    }

    /** Solid floor, feet and head not solid; unloaded cells never count as floor. */
    private boolean standable(BlockPos feet) {
        return solid(feet.offset(0, -1, 0)) && !solid(feet) && !solid(feet.offset(0, 1, 0));
    }

    private boolean solid(BlockPos p) {
        BlockState s = blocks.get(p).orElse(null);
        return s != null && catalog.kind(s.key()) == BlockKind.SOLID;
    }
}
