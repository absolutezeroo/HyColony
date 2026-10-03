package dev.hycolony.plugin.block;

import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.server.core.asset.type.fluid.Fluid;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.chunk.section.FluidSection;
import com.hypixel.hytale.server.core.universe.world.storage.ChunkStore;
import dev.hycolony.core.kernel.BlockPos;
import org.jspecify.annotations.Nullable;

/** Chunk-section access shared by the world adapter and the block breaker. Never loads a chunk. World thread only. */
public final class HytaleSections {
    private HytaleSections() {}

    /**
     * The loaded section holding {@code pos}; null when its chunk is not loaded. Null rather than Optional: every
     * block read and write goes through here, so it allocates nothing.
     */
    public static @Nullable Ref<ChunkStore> section(World world, BlockPos pos) {
        Ref<ChunkStore> sec = world.getChunkStore().getChunkSectionReferenceAtBlock(pos.x(), pos.y(), pos.z());
        return sec != null && sec.isValid() ? sec : null;
    }

    /** The fluid id at {@code pos}, which a block may share; {@link Fluid#EMPTY_ID} for none. */
    public static int fluidId(Store<ChunkStore> store, Ref<ChunkStore> sec, BlockPos pos) {
        FluidSection fluids = store.getComponent(sec, FluidSection.getComponentType());
        return fluids == null ? Fluid.EMPTY_ID : fluids.getFluidId(pos.x(), pos.y(), pos.z());
    }

    /**
     * Fills {@code pos} with fluid {@code fluidId} at its full level, under the block there if any; false for an
     * unknown or empty fluid.
     */
    public static boolean placeFluid(Store<ChunkStore> store, Ref<ChunkStore> sec, BlockPos pos, String fluidId) {
        Fluid fluid = Fluid.getAssetMap().getAsset(fluidId);
        if (fluid == null || fluid.equals(Fluid.EMPTY)) {
            return false;
        }
        store.ensureAndGetComponent(sec, FluidSection.getComponentType())
                .setFluid(pos.x(), pos.y(), pos.z(), fluid, (byte) fluid.getMaxFluidLevel());
        return true;
    }

    /** Removes the fluid at {@code pos}, if any. */
    public static void clearFluid(Store<ChunkStore> store, Ref<ChunkStore> sec, BlockPos pos) {
        FluidSection fluids = store.getComponent(sec, FluidSection.getComponentType());
        if (fluids != null && fluids.getFluidId(pos.x(), pos.y(), pos.z()) != Fluid.EMPTY_ID) {
            fluids.setFluid(pos.x(), pos.y(), pos.z(), Fluid.EMPTY_ID, (byte) 0);
        }
    }
}
