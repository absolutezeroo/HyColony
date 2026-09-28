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
import java.util.Optional;

/** A block as placed in the world: its type and its turn (VariantRotation). World thread. */
record PlacedBlock(BlockType type, RotationTuple turn) {
    /** The block at {@code pos}; empty for air or an unloaded chunk. */
    static Optional<PlacedBlock> at(World world, BlockPos pos) {
        Ref<ChunkStore> section = HytaleSections.section(world, pos);
        BlockSection blocks = section == null
                ? null
                : world.getChunkStore().getStore().getComponent(section, BlockSection.getComponentType());
        int id = blocks == null ? BlockType.EMPTY_ID : blocks.get(pos.x(), pos.y(), pos.z());
        BlockType type =
                id == BlockType.EMPTY_ID ? null : BlockType.getAssetMap().getAsset(id);
        return blocks == null || type == null
                ? Optional.empty()
                : Optional.of(new PlacedBlock(type, blocks.getRotation(pos.x(), pos.y(), pos.z())));
    }

    /**
     * The turn as a block entity's rotation: a block's yaw is half a turn from its entity's (FallingBlock
     * generateFallingBlock adds PI, FallingBlockTickingSystem takes it off).
     */
    Rotation3f rotation() {
        float pitch = (float) turn.pitch().getRadians();
        float yaw = (float) (turn.yaw().getRadians() + Math.PI);
        return new Rotation3f(pitch, yaw, (float) turn.roll().getRadians());
    }
}
