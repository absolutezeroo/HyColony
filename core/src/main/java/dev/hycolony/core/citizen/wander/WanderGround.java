package dev.hycolony.core.citizen.wander;

import dev.hycolony.core.kernel.BlockPos;
import dev.hycolony.core.kernel.Vec3;
import dev.hycolony.core.kernel.item.BlockKind;
import dev.hycolony.core.kernel.item.BlockState;
import dev.hycolony.core.kernel.port.ItemCatalog;
import dev.hycolony.core.kernel.port.WorldBlocks;
import java.util.Optional;

/**
 * Where a wander walk to a column ends (MC PathJobRandomPos ends on a walkable cell, never over water): the standable
 * cell nearest the citizen's height, {@link #HALF_HEIGHT} blocks up or down at most; a column without one is a spot
 * only when it is open there and holds no leaves nor fluid the citizen could climb or drop onto.
 *
 * <p>Deviation from MC (Hytale world): MC climbs 1.3 blocks (PathingConstants.MAX_JUMP_HEIGHT), so a treetop is out of
 * its reach; our citizens climb 3 (plugin-b-api.md 39) and could stand on a trunk's top within the leaves, so a cell
 * whose feet or head are in leaves is no wander spot.
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
     * column has none but is {@link #open} (a slope, unknown ground); empty else.
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
        return open(at) ? Optional.of(target) : Optional.empty();
    }

    private static Vec3 centre(BlockPos feet) {
        return new Vec3(feet.x() + 0.5, feet.y(), feet.z() + 0.5);
    }

    /**
     * Room for a body at {@code at}, and no leaves nor fluid from one block below the lowest feet height tried to one
     * above the highest: the nav could leave it on a treetop or in a pond.
     */
    private boolean open(BlockPos at) {
        if (blocked(at) || blocked(at.offset(0, 1, 0))) {
            return false;
        }
        for (int dy = -HALF_HEIGHT - 1; dy <= HALF_HEIGHT + 1; dy++) {
            BlockState s = blocks.get(at.offset(0, dy, 0)).orElse(null);
            if (s != null && (catalog.isLeaves(s.key()) || catalog.kind(s.key()) == BlockKind.FLUID)) {
                return false;
            }
        }
        return true;
    }

    /**
     * A solid floor and room for feet and head. Leaves are never one: Hytale's have no Material (Plant_Leaves_*.json),
     * so BlockType's default Empty makes them NON_SOLID, walked through.
     */
    private boolean standable(BlockPos feet) {
        BlockState floor = blocks.get(feet.offset(0, -1, 0)).orElse(null);
        return floor != null
                && catalog.kind(floor.key()) == BlockKind.SOLID
                && !blocked(feet)
                && !blocked(feet.offset(0, 1, 0));
    }

    /**
     * No room for a body to stand: a solid block, a block no worker may break (a hut block, an ungatherable block), a
     * fluid, or leaves (the treetop, not to be climbed into).
     */
    private boolean blocked(BlockPos p) {
        BlockState s = blocks.get(p).orElse(null);
        if (s == null) {
            return false;
        }
        BlockKind kind = catalog.kind(s.key());
        return kind == BlockKind.SOLID
                || kind == BlockKind.UNBREAKABLE
                || kind == BlockKind.FLUID
                || catalog.isLeaves(s.key());
    }
}
