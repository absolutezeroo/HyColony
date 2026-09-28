package dev.hycolony.plugin.ornament.cutter;

import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.server.core.asset.type.blocktype.config.BlockType;
import com.hypixel.hytale.server.core.entity.entities.player.windows.ContainerBlockWindow;
import com.hypixel.hytale.server.core.modules.block.BlockModule;
import com.hypixel.hytale.server.core.modules.block.components.ItemContainerBlock;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.chunk.section.BlockSection;
import com.hypixel.hytale.server.core.universe.world.storage.ChunkStore;
import java.util.Optional;
import org.joml.Vector3i;

/**
 * A placed cutter as the world holds it: its 2-slot container component and a fresh container window on it, turned as
 * the block (as vanilla OpenContainerInteraction reads a chest).
 */
record CutterBlock(ItemContainerBlock box, ContainerBlockWindow window) {
    /** The cutter at pos; empty when the block has no container or its chunk section is not loaded. */
    static Optional<CutterBlock> at(World world, Vector3i pos) {
        ChunkStore chunks = world.getChunkStore();
        Ref<ChunkStore> blockRef = BlockModule.getBlockEntity(world, pos.x, pos.y, pos.z);
        ItemContainerBlock box = blockRef == null
                ? null
                : chunks.getStore().getComponent(blockRef, ItemContainerBlock.getComponentType());
        Ref<ChunkStore> section = chunks.getChunkSectionReferenceAtBlock(pos.x, pos.y, pos.z);
        BlockSection blocks =
                section == null ? null : chunks.getStore().getComponent(section, BlockSection.getComponentType());
        BlockType type = blocks == null ? null : BlockType.getAssetMap().getAsset(blocks.get(pos.x, pos.y, pos.z));
        if (box == null || blocks == null || type == null) {
            return Optional.empty();
        }
        int rotation = blocks.getRotationIndex(pos.x, pos.y, pos.z);
        return Optional.of(new CutterBlock(
                box, new ContainerBlockWindow(pos.x, pos.y, pos.z, rotation, type, box.getItemContainer())));
    }
}
