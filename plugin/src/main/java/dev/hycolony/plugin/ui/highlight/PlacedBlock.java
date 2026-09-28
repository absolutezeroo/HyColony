package dev.hycolony.plugin.ui.highlight;

import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.math.vector.Rotation3f;
import com.hypixel.hytale.server.core.asset.type.blocktype.config.BlockType;
import com.hypixel.hytale.server.core.asset.type.blocktype.config.RotationTuple;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.chunk.section.BlockSection;
import com.hypixel.hytale.server.core.universe.world.storage.ChunkStore;
import dev.hycolony.core.kernel.BlockPos;
import dev.hycolony.plugin.block.HytaleSections;
import org.jspecify.annotations.Nullable;

/** A block as placed in the world: its type and its turn (VariantRotation). World thread. */
record PlacedBlock(BlockType type, RotationTuple turn) {
    /** The block at {@code pos}; null for air or an unloaded chunk. */
    static @Nullable PlacedBlock at(World world, BlockPos pos) {
        Ref<ChunkStore> section = HytaleSections.section(world, pos);
        BlockSection blocks = section == null
                ? null
                : world.getChunkStore().getStore().getComponent(section, BlockSection.getComponentType());
        int id = blocks == null ? BlockType.EMPTY_ID : blocks.get(pos.x(), pos.y(), pos.z());
        BlockType type =
                id == BlockType.EMPTY_ID ? null : BlockType.getAssetMap().getAsset(id);
        return blocks == null || type == null
                ? null
                : new PlacedBlock(type, blocks.getRotation(pos.x(), pos.y(), pos.z()));
    }

    /** The turn as an entity rotation (Rotation.java builds its Rotation3f the same way). */
    Rotation3f rotation() {
        return new Rotation3f(
                (float) turn.pitch().getRadians(), (float) turn.yaw().getRadians(), (float)
                        turn.roll().getRadians());
    }
}
