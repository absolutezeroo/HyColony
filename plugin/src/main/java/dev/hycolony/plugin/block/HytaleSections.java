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

    /** The loaded section holding {@code pos}; null when its chunk is not loaded. */
    public static @Nullable Ref<ChunkStore> section(World world, BlockPos pos) {
        Ref<ChunkStore> sec = world.getChunkStore().getChunkSectionReferenceAtBlock(pos.x(), pos.y(), pos.z());
        return sec != null && sec.isValid() ? sec : null;
    }

    /** Removes the fluid at {@code pos}, if any. */
    public static void clearFluid(Store<ChunkStore> store, Ref<ChunkStore> sec, BlockPos pos) {
        FluidSection fluids = store.getComponent(sec, FluidSection.getComponentType());
        if (fluids != null && fluids.getFluidId(pos.x(), pos.y(), pos.z()) != Fluid.EMPTY_ID) {
            fluids.setFluid(pos.x(), pos.y(), pos.z(), Fluid.EMPTY_ID, (byte) 0);
        }
    }
}
