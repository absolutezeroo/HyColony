package dev.hycolony.plugin.adapter;

import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.server.core.asset.type.blocktype.config.BlockType;
import com.hypixel.hytale.server.core.modules.interaction.BlockHarvestUtils;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.chunk.section.BlockSection;
import com.hypixel.hytale.server.core.universe.world.storage.ChunkStore;
import dev.hycolony.core.kernel.BlockPos;
import org.joml.Vector3i;

/** Section-API block helpers (no deprecated World/WorldChunk calls). World thread only. */
public final class HytaleBlocks {
    private final World world;

    public HytaleBlocks(World world) {
        this.world = world;
    }

    /** Removes the block at pos and drops {@code dropItemId} x1, like a player break. */
    public void removeWithDrop(BlockPos pos, String dropItemId) {
        ChunkStore cs = world.getChunkStore();
        Ref<ChunkStore> section = cs.getChunkSectionReferenceAtBlock(pos.x(), pos.y(), pos.z());
        if (section == null) {
            return;
        }
        BlockSection blocks = cs.getStore().getComponent(section, BlockSection.getComponentType());
        BlockType type = BlockType.getAssetMap().getAsset(blocks.get(pos.x(), pos.y(), pos.z()));
        BlockHarvestUtils.naturallyRemoveBlock(new Vector3i(pos.x(), pos.y(), pos.z()), type,
                blocks.getFiller(pos.x(), pos.y(), pos.z()), 1, dropItemId, null, 0, section,
                world.getEntityStore().getStore(), cs.getStore());
    }
}
