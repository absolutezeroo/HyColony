package dev.hylens.plugin.watch;

import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.math.util.ChunkUtil;
import com.hypixel.hytale.protocol.BlockMaterial;
import com.hypixel.hytale.server.core.asset.type.blocktype.config.BlockType;
import com.hypixel.hytale.server.core.asset.type.fluid.Fluid;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.chunk.section.BlockSection;
import com.hypixel.hytale.server.core.universe.world.chunk.section.FluidSection;
import com.hypixel.hytale.server.core.universe.world.storage.ChunkStore;
import dev.hylens.core.draw.Ground;
import java.util.OptionalInt;
import org.jspecify.annotations.Nullable;

/**
 * The ground of a world, read on its thread from the loaded chunk sections, as TargetCells reads them: a body stands in
 * a cell that is neither solid nor water, over one that is (it swims on water). Never loads a chunk.
 */
final class HytaleGround implements Ground {
    /** The cells tried around the line's height, nearest first. */
    private static final int[] OFFSETS = {0, 1, -1, 2, -2, -3, -4};

    private static final int UNKNOWN = -1;
    private static final int OPEN = 0;
    private static final int SOLID = 1;

    private final World world;

    HytaleGround(World world) {
        this.world = world;
    }

    /** The nearest open cell to {@code nearY} over one that bears a body; empty over a void or an unloaded section. */
    @Override
    public OptionalInt standY(int x, int z, int nearY) {
        for (int offset : OFFSETS) {
            int y = nearY + offset;
            int under = cell(x, y - 1, z);
            int here = cell(x, y, z);
            if (under == UNKNOWN || here == UNKNOWN) {
                return OptionalInt.empty();
            }
            if (under == SOLID && here == OPEN) {
                return OptionalInt.of(y);
            }
        }
        return OptionalInt.empty();
    }

    /** Whether the cell bears a body (solid, or water), is open, or unknown (out of height, or not loaded). */
    private int cell(int x, int y, int z) {
        if (y < 0 || y >= ChunkUtil.HEIGHT) {
            return UNKNOWN;
        }
        @Nullable Ref<ChunkStore> sec = world.getChunkStore().getChunkSectionReferenceAtBlock(x, y, z);
        if (sec == null || !sec.isValid()) {
            return UNKNOWN;
        }
        @Nullable
        BlockSection blocks = world.getChunkStore().getStore().getComponent(sec, BlockSection.getComponentType());
        if (blocks == null) {
            return UNKNOWN;
        }
        @Nullable BlockType type = BlockType.getAssetMap().getAsset(blocks.get(x, y, z));
        return (type != null && type.getMaterial() == BlockMaterial.Solid) || water(sec, x, y, z) ? SOLID : OPEN;
    }

    /** Whether water (any fluid) fills the cell of section {@code sec}. */
    private boolean water(Ref<ChunkStore> sec, int x, int y, int z) {
        @Nullable
        FluidSection fluids = world.getChunkStore().getStore().getComponent(sec, FluidSection.getComponentType());
        return fluids != null && fluids.getFluidId(x, y, z) != Fluid.EMPTY_ID;
    }
}
