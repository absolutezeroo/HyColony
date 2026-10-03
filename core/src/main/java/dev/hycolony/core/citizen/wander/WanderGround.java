package dev.hycolony.core.citizen.wander;

import dev.hycolony.core.kernel.BlockPos;
import dev.hycolony.core.kernel.Vec3;
import dev.hycolony.core.kernel.item.BlockKind;
import dev.hycolony.core.kernel.item.BlockState;
import dev.hycolony.core.kernel.port.ItemCatalog;
import dev.hycolony.core.kernel.port.WorldBlocks;
import java.util.Optional;

/**
 * Where a wander walk to a column ends (MC PathJobRandomPos ends on a walkable cell): the standable cell nearest the
 * citizen's height, {@link #HALF_HEIGHT} blocks up or down at most.
 *
 * <p>Deviation from MC (Hytale world): MC climbs 1.3 blocks (PathingConstants.MAX_JUMP_HEIGHT), so a treetop is out of
 * its reach though leaves are walkable (SurfaceType); our citizens climb 3 (plugin-b-api.md 39), so leaves are no floor
 * here, and a column a trunk or leaves block at the citizen's height is no wander spot.
 */
final class WanderGround {
    /** Feet heights tried above and below the citizen's. */
    static final int HALF_HEIGHT = 3;

    private final WorldBlocks blocks;
    private final ItemCatalog catalog;

    WanderGround(WorldBlocks blocks, ItemCatalog catalog) {
        this.blocks = blocks;
        this.catalog = catalog;
    }

    /**
     * The centre of the standable cell of {@code target}'s column nearest its height; {@code target} itself when the
     * column has none but is open at that height (a slope, unknown ground); empty when solid or leaves block it.
     */
    Optional<Vec3> of(Vec3 target) {
        BlockPos at = target.toBlockPos();
        for (int d = 0; d <= HALF_HEIGHT; d++) {
            if (standable(at.offset(0, d, 0))) {
                return Optional.of(centre(at.offset(0, d, 0)));
            }
            if (d > 0 && standable(at.offset(0, -d, 0))) {
                return Optional.of(centre(at.offset(0, -d, 0)));
            }
        }
        return blocked(at) || blocked(at.offset(0, 1, 0)) ? Optional.empty() : Optional.of(target);
    }

    private static Vec3 centre(BlockPos feet) {
        return new Vec3(feet.x() + 0.5, feet.y(), feet.z() + 0.5);
    }

    /** A floor that is no leaves, and room for feet and head. */
    private boolean standable(BlockPos feet) {
        BlockState floor = blocks.get(feet.offset(0, -1, 0)).orElse(null);
        return floor != null
                && catalog.kind(floor.key()) == BlockKind.SOLID
                && !catalog.isLeaves(floor.key())
                && !blocked(feet)
                && !blocked(feet.offset(0, 1, 0));
    }

    /** A solid block or leaves: no room for a body. */
    private boolean blocked(BlockPos p) {
        BlockState s = blocks.get(p).orElse(null);
        return s != null && (catalog.kind(s.key()) == BlockKind.SOLID || catalog.isLeaves(s.key()));
    }
}
