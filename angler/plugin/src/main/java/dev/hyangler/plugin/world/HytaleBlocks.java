package dev.hyangler.plugin.world;

import com.hypixel.hytale.assetstore.AssetExtraInfo;
import com.hypixel.hytale.assetstore.AssetRegistry;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.logger.HytaleLogger;
import com.hypixel.hytale.math.util.ChunkUtil;
import com.hypixel.hytale.protocol.BlockMaterial;
import com.hypixel.hytale.server.core.asset.type.blocktype.config.BlockType;
import com.hypixel.hytale.server.core.asset.type.fluid.Fluid;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.chunk.heightmap.HeightmapColumn;
import com.hypixel.hytale.server.core.universe.world.chunk.section.BlockSection;
import com.hypixel.hytale.server.core.universe.world.chunk.section.FluidSection;
import com.hypixel.hytale.server.core.universe.world.storage.ChunkStore;
import dev.hyangler.core.port.BlockKind;
import dev.hyangler.core.port.BlockProbe;
import java.util.Set;
import java.util.logging.Level;
import org.jspecify.annotations.Nullable;

/**
 * Blocks around a bobber for the core (spec § 5, § 6.4, fishing-hytale.md § 5.6, § 5.7), as vanilla's
 * getOpenWaterTypeForBlock: still water (a water fluid of MaxFluidLevel 1, Water_Source) or flowing water (Water,
 * Water_Finite) in a cell with no solid block; air (no fluid and BlockMaterial.Empty) or a water lily of the id-map
 * (vanilla's lily pad; most of Hytale's are Solid); or anything else. The sky through the column's heightmap, which
 * skips transparent blocks such as water (WaterGrowthModifierAsset.java:232-248). Never loads a chunk: an unloaded
 * one is OTHER, without sky.
 */
final class HytaleBlocks implements BlockProbe {
    private static final HytaleLogger LOG = HytaleLogger.forEnclosingClass();
    /** Water_Source's {@code "Tags": {"Fluid": ["Water"]}}, inherited by Water and Water_Finite (MotionCells). */
    private static final int WATER_TAG = AssetRegistry.getOrCreateTagIndex("Fluid=Water");

    private final World world;
    private final Set<String> surface;
    private boolean warned;

    /** surface: the id-map's blocks that float on water and keep it open, as vanilla's lily pad. */
    HytaleBlocks(World world, Set<String> surface) {
        this.world = world;
        this.surface = surface;
    }

    @Override
    public BlockKind kind(int x, int y, int z) {
        try {
            ChunkStore chunks = world.getChunkStore();
            Ref<ChunkStore> sec = chunks.getChunkSectionReferenceAtBlock(x, y, z);
            if (sec == null || !sec.isValid()) {
                return BlockKind.OTHER;
            }
            BlockSection blocks = chunks.getStore().getComponent(sec, BlockSection.getComponentType());
            FluidSection fluids = chunks.getStore().getComponent(sec, FluidSection.getComponentType());
            return classify(
                    blocks == null ? null : BlockType.getAssetMap().getAsset(blocks.get(x, y, z)),
                    fluids == null ? Fluid.EMPTY_ID : fluids.getFluidId(x, y, z));
        } catch (RuntimeException e) {
            failed(e, "block");
            return BlockKind.OTHER;
        }
    }

    /** A cell's kind from its block and its fluid (vanilla getOpenWaterTypeForBlock). */
    private BlockKind classify(@Nullable BlockType type, int fluid) {
        boolean empty = type != null && type.getMaterial() == BlockMaterial.Empty;
        if (fluid != Fluid.EMPTY_ID) {
            // Water counts only where nothing solid fills the cell.
            return empty ? water(Fluid.getAssetMap().getAsset(fluid)) : BlockKind.OTHER;
        }
        if (empty || (type != null && surface.contains(type.getId()))) {
            return BlockKind.AIR;
        }
        return BlockKind.OTHER;
    }

    @Override
    public boolean skyVisible(int x, int y, int z) {
        try {
            ChunkStore chunks = world.getChunkStore();
            Ref<ChunkStore> column = chunks.getChunkReference(ChunkUtil.indexChunkFromBlock(x, z));
            if (column == null || !column.isValid()) {
                return false;
            }
            HeightmapColumn height = chunks.getStore().getComponent(column, HeightmapColumn.getComponentType());
            if (height == null) {
                return false;
            }
            int top = height.getHeight(x, z);
            return top == HeightmapColumn.NO_HEIGHT || top <= y;
        } catch (RuntimeException e) {
            failed(e, "sky");
            return false;
        }
    }

    private static BlockKind water(@Nullable Fluid fluid) {
        AssetExtraInfo.Data data = fluid == null ? null : fluid.getData();
        if (fluid == null || data == null || !data.getExpandedTagIndexes().contains(WATER_TAG)) {
            return BlockKind.OTHER;
        }
        return fluid.getMaxFluidLevel() == 1 ? BlockKind.WATER_SOURCE : BlockKind.WATER_FLOWING;
    }

    private void failed(RuntimeException e, String what) {
        LOG.at(warned ? Level.FINE : Level.WARNING).withCause(e).log("HyAngler: %s read failed", what);
        warned = true;
    }
}
