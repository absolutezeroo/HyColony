package dev.hycolony.core.farming.job;

import dev.hycolony.core.farming.CropState;
import dev.hycolony.core.farming.FarmingAccess;
import dev.hycolony.core.kernel.BlockPos;
import dev.hycolony.core.kernel.item.BlockKind;
import dev.hycolony.core.kernel.item.BlockState;
import dev.hycolony.core.kernel.port.ItemCatalog;
import dev.hycolony.core.kernel.port.WorldBlocks;
import java.util.Optional;

/**
 * What the farmer finds at one cell of a field (MC EntityAIWorkFarmer getSurfacePos, isNoPartOfField and
 * find*Surface). Reads only. Deviation from MC: MC's scans break the plant above a cell to hoe and spend fertilizer
 * on the crops they look at; here the pass does both, never the scan.
 */
final class FieldScan {
    /** MC MAX_DEPTH: how far up or down from the field block's level the ground is searched. */
    static final int MAX_DEPTH = 5;

    private final WorldBlocks world;
    private final ItemCatalog catalog;
    private final FarmingAccess farming;

    FieldScan(WorldBlocks world, ItemCatalog catalog, FarmingAccess farming) {
        this.world = world;
        this.catalog = catalog;
        this.farming = farming;
    }

    /**
     * MC getSurfacePos: from {@code column} (one below the field block, at the cell), the top solid block within
     * {@link #MAX_DEPTH} up or down; empty past that or on an unloaded chunk. A fluid counts as solid, a crop never.
     */
    Optional<BlockPos> surface(BlockPos column) {
        BlockPos pos = column;
        int depth = 0;
        while (Math.abs(depth) <= MAX_DEPTH && world.isLoaded(pos)) {
            boolean solid = isSolid(pos);
            if (solid && depth < 0) {
                return Optional.of(pos);
            }
            if (!solid && depth > 0) {
                return Optional.of(pos.offset(0, -1, 0));
            }
            depth += solid ? 1 : -1;
            pos = pos.offset(0, solid ? 1 : -1, 0);
        }
        return Optional.empty();
    }

    /** MC isNoPartOfField: the cell is air, or a fence, gate or wall stands on it. */
    boolean isNoPartOfField(BlockPos surface) {
        return kind(surface) == BlockKind.AIR || farming.isFieldBarrier(surface.offset(0, 1, 0));
    }

    /** MC findHoeableSurface: tillable soil in the field, not tilled yet, with no crop nor field block on it. */
    Optional<BlockPos> hoeable(BlockPos column) {
        return surface(column)
                .filter(s -> !isNoPartOfField(s)
                        && farming.crop(s.offset(0, 1, 0)) == CropState.NONE
                        && !farming.isFieldBlock(s.offset(0, 1, 0))
                        && farming.isTillable(s)
                        && !farming.isTilled(s));
    }

    /** MC findPlantableSurface: tilled soil in the field with nothing growing on it, not the field block. */
    Optional<BlockPos> plantable(BlockPos column) {
        return surface(column)
                .filter(s -> !isNoPartOfField(s)
                        && farming.crop(s.offset(0, 1, 0)) == CropState.NONE
                        && !farming.isFieldBlock(s)
                        && farming.isTilled(s));
    }

    /**
     * MC findHarvestableSurface: a mature crop stands on the cell. Deviation from MC: no fertilizer makes an unripe
     * crop ripe here (Hytale has no such item).
     */
    Optional<BlockPos> harvestable(BlockPos column) {
        return surface(column).filter(s -> farming.crop(s.offset(0, 1, 0)) == CropState.MATURE);
    }

    private boolean isSolid(BlockPos pos) {
        if (farming.crop(pos) != CropState.NONE) {
            return false;
        }
        BlockKind kind = kind(pos);
        return kind == BlockKind.SOLID || kind == BlockKind.UNBREAKABLE || kind == BlockKind.FLUID;
    }

    private BlockKind kind(BlockPos pos) {
        return world.get(pos).map(BlockState::key).map(catalog::kind).orElse(BlockKind.AIR);
    }
}
