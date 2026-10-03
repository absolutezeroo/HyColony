package dev.hycolony.plugin.npc.motion;

import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.protocol.BlockMaterial;
import com.hypixel.hytale.server.core.asset.type.blocktype.config.BlockType;
import com.hypixel.hytale.server.core.asset.type.fluid.Fluid;
import com.hypixel.hytale.server.core.universe.world.chunk.section.BlockSection;
import com.hypixel.hytale.server.core.universe.world.chunk.section.FluidSection;
import com.hypixel.hytale.server.core.universe.world.storage.ChunkStore;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import javax.annotation.Nullable;

/** The cell reads a citizen's motion systems need: solid blocks and fluids. Never loads a chunk; world thread only. */
final class MotionCells {
    private MotionCells() {}

    /** Whether the block at {@code x y z} is solid; false where its section is not loaded. */
    static boolean solid(Store<EntityStore> store, int x, int y, int z) {
        ChunkStore chunks = store.getExternalData().getWorld().getChunkStore();
        @Nullable Ref<ChunkStore> sec = section(chunks, x, y, z);
        if (sec == null) {
            return false;
        }
        @Nullable BlockSection blocks = chunks.getStore().getComponent(sec, BlockSection.getComponentType());
        if (blocks == null) {
            return false;
        }
        @Nullable BlockType type = BlockType.getAssetMap().getAsset(blocks.get(x, y, z));
        return type != null && type.getMaterial() == BlockMaterial.Solid;
    }

    /** Whether a fluid fills the cell at {@code x y z}; false where its section is not loaded. */
    static boolean fluid(Store<EntityStore> store, int x, int y, int z) {
        ChunkStore chunks = store.getExternalData().getWorld().getChunkStore();
        @Nullable Ref<ChunkStore> sec = section(chunks, x, y, z);
        if (sec == null) {
            return false;
        }
        @Nullable FluidSection fluids = chunks.getStore().getComponent(sec, FluidSection.getComponentType());
        return fluids != null && fluids.getFluidId(x, y, z) != Fluid.EMPTY_ID;
    }

    private static @Nullable Ref<ChunkStore> section(ChunkStore chunks, int x, int y, int z) {
        @Nullable Ref<ChunkStore> sec = chunks.getChunkSectionReferenceAtBlock(x, y, z);
        return sec == null || !sec.isValid() ? null : sec;
    }
}
