package dev.hydomum.plugin.connect;

import com.hypixel.hytale.math.shape.Box;
import com.hypixel.hytale.math.util.ChunkUtil;
import com.hypixel.hytale.protocol.BlockMaterial;
import com.hypixel.hytale.server.core.asset.type.blockhitbox.BlockBoundingBoxes;
import com.hypixel.hytale.server.core.asset.type.blocktype.config.BlockType;
import com.hypixel.hytale.server.core.universe.world.chunk.section.BlockSection;
import com.hypixel.hytale.server.core.universe.world.storage.ChunkStore;
import dev.hydomum.core.connect.Footprint;
import dev.hydomum.core.connect.Joiner;
import dev.hydomum.core.connect.WallState;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.joml.Vector3ic;

/**
 * The block above a wall, as MC WallBlock.updateShape reads it: its collision's bottom face, and whether it is a wall
 * with its post raised.
 */
record HytaleAbove(Footprint footprint, boolean wallPost) {
    // How far (blocks) above its bottom a collision box may start and still be part of the bottom face.
    private static final double BOTTOM = 1e-3;

    /**
     * The block above at, for a wall's top (MC WallBlock.updateShape reads its collision's bottom face): nothing above
     * the world's top; empty when its section is not loaded. Only a solid block collides (BlockDataProvider).
     */
    static Optional<HytaleAbove> read(ChunkStore chunkStore, Vector3ic at) {
        int x = at.x();
        int y = at.y() + 1;
        int z = at.z();
        if (y >= ChunkUtil.HEIGHT) {
            return Optional.of(new HytaleAbove(Footprint.NONE, false));
        }
        Optional<BlockSection> loaded = HytaleNeighbours.section(chunkStore, x, y, z);
        if (loaded.isEmpty()) {
            return Optional.empty();
        }
        BlockSection section = loaded.get();
        int index = section.get(x, y, z);
        BlockType type = BlockType.getAssetMap().getAsset(index);
        if (type == null) {
            return Optional.of(new HytaleAbove(Footprint.NONE, false));
        }
        // Deviation from MC: only a HyDomum wall is read as a wall with its post raised (MC: any WallBlock with UP); a
        // vanilla wall above counts by its collision only.
        boolean wallPost = type.getConnectedBlockRuleSet() instanceof HytaleFenceRules rules
                && rules.joiner() == Joiner.WALL
                && rules.getShapesForBlockType(index).stream().anyMatch(WallState::hasPost);
        // Deviation from MC: a filler cell of a larger block is read as nothing above (its boxes are the origin's).
        boolean solid = type.getMaterial() == BlockMaterial.Solid && section.getFiller(x, y, z) == 0;
        return Optional.of(new HytaleAbove(
                solid ? bottomFace(type, section.getRotationIndex(x, y, z)) : Footprint.NONE, wallPost));
    }

    /** The bottom face of type's hitbox turned by rotation: its boxes from the block's bottom, seen from below. */
    private static Footprint bottomFace(BlockType type, int rotation) {
        BlockBoundingBoxes hitbox = BlockBoundingBoxes.getAssetMap().getAsset(type.getHitboxTypeIndex());
        if (hitbox == null) {
            return Footprint.NONE;
        }
        List<Footprint.Rect> rects = new ArrayList<>();
        for (Box box : hitbox.get(rotation).getDetailBoxes()) {
            if (box.min.y <= BOTTOM && box.max.y > BOTTOM) {
                rects.add(new Footprint.Rect(box.min.x, box.min.z, box.max.x, box.max.z));
            }
        }
        return new Footprint(rects);
    }
}
