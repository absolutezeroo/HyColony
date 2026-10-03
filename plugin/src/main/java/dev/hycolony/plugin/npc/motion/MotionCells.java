package dev.hycolony.plugin.npc.motion;

import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.protocol.BlockMaterial;
import com.hypixel.hytale.server.core.asset.type.blocktype.config.BlockType;
import com.hypixel.hytale.server.core.asset.type.fluid.Fluid;
import com.hypixel.hytale.server.core.asset.type.fluidfx.config.FluidFX;
import com.hypixel.hytale.server.core.universe.world.chunk.section.BlockSection;
import com.hypixel.hytale.server.core.universe.world.chunk.section.FluidSection;
import com.hypixel.hytale.server.core.universe.world.storage.ChunkStore;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import javax.annotation.Nullable;

/** The cell reads a citizen's motion systems need: solid blocks and water. Never loads a chunk; world thread only. */
final class MotionCells {
    /** The fluid effects every water fluid shares ({@code FluidFXId} of Server/Item/Block/Fluids/Water_Source.json). */
    private static final String WATER_FX = "Water";

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

    /**
     * Whether water fills the cell at {@code x y z}: a fluid with the {@code Water} fluid effects (Water, Water_Source,
     * Water_Finite), as MC swims only in water (AbstractPathJob isWater, MovementHandler); false for another fluid, or
     * where the section is not loaded.
     */
    static boolean water(Store<EntityStore> store, int x, int y, int z) {
        ChunkStore chunks = store.getExternalData().getWorld().getChunkStore();
        @Nullable Ref<ChunkStore> sec = section(chunks, x, y, z);
        if (sec == null) {
            return false;
        }
        @Nullable FluidSection fluids = chunks.getStore().getComponent(sec, FluidSection.getComponentType());
        int id = fluids == null ? Fluid.EMPTY_ID : fluids.getFluidId(x, y, z);
        @Nullable
        Fluid fluid = id == Fluid.EMPTY_ID ? null : Fluid.getAssetMap().getAsset(id);
        @Nullable FluidFX fx = fluid == null ? null : FluidFX.getAssetMap().getAsset(fluid.getFluidFXIndex());
        return fx != null && WATER_FX.equals(fx.getId());
    }

    private static @Nullable Ref<ChunkStore> section(ChunkStore chunks, int x, int y, int z) {
        @Nullable Ref<ChunkStore> sec = chunks.getChunkSectionReferenceAtBlock(x, y, z);
        return sec == null || !sec.isValid() ? null : sec;
    }
}
