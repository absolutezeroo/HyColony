package dev.hycolony.plugin.farming;

import com.hypixel.hytale.builtin.adventure.farming.states.TilledSoilBlock;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.server.core.asset.type.blocktype.config.BlockGathering;
import com.hypixel.hytale.server.core.asset.type.blocktype.config.BlockType;
import com.hypixel.hytale.server.core.asset.type.blocktype.config.HarvestingDropType;
import com.hypixel.hytale.server.core.inventory.ItemStack;
import com.hypixel.hytale.server.core.modules.block.BlockModule;
import com.hypixel.hytale.server.core.modules.interaction.BlockHarvestUtils;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.chunk.section.BlockSection;
import com.hypixel.hytale.server.core.universe.world.chunk.section.ChunkSection;
import com.hypixel.hytale.server.core.universe.world.storage.ChunkStore;
import dev.hycolony.core.kernel.BlockPos;
import dev.hycolony.core.kernel.item.ItemAmount;
import dev.hycolony.core.kernel.item.ItemKey;
import dev.hycolony.plugin.block.HytaleSections;
import java.util.ArrayList;
import java.util.List;
import org.jspecify.annotations.Nullable;

/**
 * The Hytale block state the farmer reads and changes beyond placing blocks (plugin-b-api § 27): a crop's exact stage
 * type, its harvest drops, and tilled soil's fertilizer. World thread; a chunk not loaded answers null, false or empty.
 */
final class FarmBlocks {
    private final World world;

    FarmBlocks(World world) {
        this.world = world;
    }

    /** The exact block type at {@code pos}, a state variant included (a crop's stage); null if unloaded or air. */
    @Nullable
    BlockType typeAt(BlockPos pos) {
        Ref<ChunkStore> section = HytaleSections.section(world, pos);
        BlockSection blocks = section == null
                ? null
                : world.getChunkStore().getStore().getComponent(section, BlockSection.getComponentType());
        if (blocks == null) {
            return null;
        }
        int id = blocks.get(pos.x(), pos.y(), pos.z());
        return id == BlockType.EMPTY_ID ? null : BlockType.getAssetMap().getAsset(id);
    }

    /** A crop is mature when its stage declares a harvest (only StageFinal does, sp3b-hytale-farming § 3.3). */
    static boolean isHarvestable(@Nullable BlockType type) {
        BlockGathering gathering = type == null ? null : type.getGathering();
        return gathering != null && gathering.isHarvestable();
    }

    /** The harvest drops of {@code type} (BlockHarvestUtils.getDrops over its Gathering.Harvest), rolled once. */
    static List<ItemAmount> harvestDrops(BlockType type) {
        BlockGathering gathering = type.getGathering();
        HarvestingDropType harvest = gathering == null ? null : gathering.getHarvest();
        if (harvest == null) {
            return List.of();
        }
        List<ItemAmount> out = new ArrayList<>();
        for (ItemStack stack : BlockHarvestUtils.getDrops(type, 1, harvest.getItemId(), harvest.getDropListId())) {
            if (stack != null && stack.getItemId() != null && stack.getQuantity() > 0) {
                out.add(new ItemAmount(new ItemKey(stack.getItemId()), stack.getQuantity()));
            }
        }
        return out;
    }

    /** The tilled soil component at {@code pos}; null if none (not tilled soil, or unloaded). */
    @Nullable
    TilledSoilBlock soil(BlockPos pos) {
        return BlockModule.getComponent(TilledSoilBlock.getComponentType(), world, pos.x(), pos.y(), pos.z());
    }

    /**
     * FertilizeSoilInteraction: the soil is fertilized for good, and it and the block above tick again so growth picks
     * the bonus up. False if {@code pos} holds no tilled soil.
     */
    boolean fertilize(BlockPos pos) {
        TilledSoilBlock soil = soil(pos);
        if (soil == null) {
            return false;
        }
        soil.setFertilized(true);
        tick(pos);
        tick(pos.offset(0, 1, 0));
        return true;
    }

    private void tick(BlockPos pos) {
        Ref<ChunkStore> section = HytaleSections.section(world, pos);
        Store<ChunkStore> store = world.getChunkStore().getStore();
        BlockSection blocks = section == null ? null : store.getComponent(section, BlockSection.getComponentType());
        ChunkSection chunk = section == null ? null : store.getComponent(section, ChunkSection.getComponentType());
        // Saved like BlockOperations.setTicking does, so the tick survives a chunk unload.
        if (blocks != null && blocks.setTicking(pos.x(), pos.y(), pos.z(), true) && chunk != null) {
            chunk.markNeedsSaving();
        }
    }
}
