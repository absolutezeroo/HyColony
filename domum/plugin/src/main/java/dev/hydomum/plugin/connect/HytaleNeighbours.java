package dev.hydomum.plugin.connect;

import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.logger.HytaleLogger;
import com.hypixel.hytale.server.core.asset.type.blocktype.config.BlockFace;
import com.hypixel.hytale.server.core.asset.type.blocktype.config.BlockFaceSupport;
import com.hypixel.hytale.server.core.asset.type.blocktype.config.BlockType;
import com.hypixel.hytale.server.core.asset.type.blocktype.config.RotationTuple;
import com.hypixel.hytale.server.core.universe.world.chunk.section.BlockSection;
import com.hypixel.hytale.server.core.universe.world.connectedblocks.ConnectedBlockRuleSet;
import com.hypixel.hytale.server.core.universe.world.connectedblocks.CustomTemplateConnectedBlockRuleSet;
import com.hypixel.hytale.server.core.universe.world.storage.ChunkStore;
import dev.hydomum.core.connect.Neighbour;
import dev.hydomum.core.connect.NeighbourKind;
import dev.hydomum.core.connect.Side;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.logging.Level;
import org.joml.Vector3ic;

/**
 * Reads a block's four horizontal neighbours as the core's {@link Neighbour}: what each is (our fences and walls by
 * their rule set's family, gates by their template shape, vanilla fences, walls and bars by the id-map's families),
 * its yaw, and whether the face it turns to us is full.
 */
public final class HytaleNeighbours {
    private static final HytaleLogger LOG = HytaleLogger.forEnclosingClass();
    private static final String GATE = "Gate";
    // A Hytale state block id: "*<block>_State_Definitions_<state>".
    private static final String STATE = "_State_Definitions_";

    private final Map<String, NeighbourKind> vanilla;

    /**
     * Reads vanilla blocks by families (hydomum/id-map.json "connections": NeighbourKind name -> block ids); an
     * unknown family name is skipped.
     */
    public HytaleNeighbours(Map<String, List<String>> families) {
        Map<String, NeighbourKind> byId = new HashMap<>();
        families.forEach((family, ids) -> kindNamed(family).ifPresent(kind -> ids.forEach(id -> byId.put(id, kind))));
        this.vanilla = Map.copyOf(byId);
    }

    /** The four neighbours of at; empty when one lies in a section not loaded, so the shape stays as it is. */
    Optional<Map<Side, Neighbour>> around(ChunkStore chunkStore, Vector3ic at) {
        Map<Side, Neighbour> around = new EnumMap<>(Side.class);
        for (Side side : Side.values()) {
            int x = at.x() + dx(side);
            int y = at.y();
            int z = at.z() + dz(side);
            Ref<ChunkStore> ref = chunkStore.getChunkSectionReferenceAtBlock(x, y, z);
            BlockSection section = ref == null || !ref.isValid()
                    ? null
                    : chunkStore.getStore().getComponent(ref, BlockSection.getComponentType());
            if (section == null) {
                return Optional.empty();
            }
            int index = section.get(x, y, z);
            int rotation = section.getRotationIndex(x, y, z);
            BlockType type = BlockType.getAssetMap().getAsset(index);
            // Deviation from MC: a filler cell is part of a larger block, its faces are not the block's own, so it has
            // no full face.
            boolean fullFace = type != null && section.getFiller(x, y, z) == 0 && fullFace(type, rotation, side);
            NeighbourKind kind = type == null ? NeighbourKind.OTHER : kind(type, index);
            around.put(
                    side,
                    new Neighbour(
                            kind, fullFace, RotationTuple.get(rotation).yaw().getDegrees() / 90));
        }
        return Optional.of(around);
    }

    /**
     * What type (asset index index) is for MC's connectsTo: its rule set's family, a gate, a vanilla family, or another
     * block. Deviation from MC: HyDomum's paper wall (a pane) keeps its own template, so walls see another block.
     */
    private NeighbourKind kind(BlockType type, int index) {
        ConnectedBlockRuleSet rules = type.getConnectedBlockRuleSet();
        if (rules instanceof HytaleFenceRules ours) {
            return ours.joiner().asNeighbour();
        }
        if (rules instanceof CustomTemplateConnectedBlockRuleSet template
                && template.getShapesForBlockType(index).contains(GATE)) {
            return NeighbourKind.GATE;
        }
        return vanilla.getOrDefault(base(type.getId()), NeighbourKind.OTHER);
    }

    /** The block id a state id belongs to ("*X_State_Definitions_Y" -> "X"); any other id as it is. */
    static String base(String id) {
        int state = id.indexOf(STATE);
        return id.startsWith("*") && state > 0 ? id.substring(1, state) : id;
    }

    /** The kind named family; empty, with a warning (the family's blocks then join as other blocks), if none is. */
    private static Optional<NeighbourKind> kindNamed(String family) {
        try {
            return Optional.of(NeighbourKind.valueOf(family));
        } catch (IllegalArgumentException e) {
            LOG.at(Level.WARNING).log("HyDomum: id-map connections names no neighbour kind %s, skipped", family);
            return Optional.empty();
        }
    }

    /** Whether type, on side of us, turns a full face to us (MC isFaceSturdy): a "Full" supporting face. */
    private static boolean fullFace(BlockType type, int rotation, Side side) {
        Map<BlockFace, BlockFaceSupport[]> supporting = type.getSupporting(rotation);
        BlockFaceSupport[] faces = supporting == null ? null : supporting.get(facingUs(side));
        if (faces == null) {
            return false;
        }
        for (BlockFaceSupport face : faces) {
            if (BlockFaceSupport.FULL_SUPPORTING_FACE.equals(face.getFaceType())) {
                return true;
            }
        }
        return false;
    }

    private static BlockFace facingUs(Side side) {
        return switch (side) {
            case NORTH -> BlockFace.SOUTH;
            case WEST -> BlockFace.EAST;
            case SOUTH -> BlockFace.NORTH;
            case EAST -> BlockFace.WEST;
        };
    }

    private static int dx(Side side) {
        return side == Side.EAST ? 1 : side == Side.WEST ? -1 : 0;
    }

    private static int dz(Side side) {
        return side == Side.SOUTH ? 1 : side == Side.NORTH ? -1 : 0;
    }
}
