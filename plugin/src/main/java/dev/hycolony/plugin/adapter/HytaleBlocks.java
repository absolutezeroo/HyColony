package dev.hycolony.plugin.adapter;

import com.hypixel.hytale.component.AddReason;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.math.vector.Rotation3f;
import com.hypixel.hytale.server.core.asset.type.blocktype.config.BlockBreakingDropType;
import com.hypixel.hytale.server.core.asset.type.blocktype.config.BlockGathering;
import com.hypixel.hytale.server.core.asset.type.blocktype.config.BlockType;
import com.hypixel.hytale.server.core.inventory.ItemStack;
import com.hypixel.hytale.server.core.modules.entity.item.ItemComponent;
import com.hypixel.hytale.server.core.modules.interaction.BlockHarvestUtils;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.chunk.section.BlockSection;
import com.hypixel.hytale.server.core.universe.world.storage.ChunkStore;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import dev.hycolony.core.kernel.BlockPos;
import dev.hycolony.core.kernel.item.ItemAmount;
import dev.hycolony.plugin.item.HytaleStacks;
import java.util.ArrayList;
import java.util.List;
import org.joml.Vector3d;
import org.joml.Vector3i;

/** Section-API block and drop helpers (no deprecated World/WorldChunk calls). World thread only. */
public final class HytaleBlocks {
    private final World world;
    private final HytaleStacks stacks;

    public HytaleBlocks(World world, HytaleStacks stacks) {
        this.world = world;
        this.stacks = stacks;
    }

    /**
     * If the block at pos is {@code expectedBlockId}, removes it and drops {@code dropItemId} x1, like a
     * player break. Anything else is left alone, so a stale position can never duplicate an item.
     */
    public void removeWithDrop(BlockPos pos, String expectedBlockId, String dropItemId) {
        ChunkStore cs = world.getChunkStore();
        Ref<ChunkStore> section = cs.getChunkSectionReferenceAtBlock(pos.x(), pos.y(), pos.z());
        if (section == null) {
            return;
        }
        BlockSection blocks = cs.getStore().getComponent(section, BlockSection.getComponentType());
        BlockType type = BlockType.getAssetMap().getAsset(blocks.get(pos.x(), pos.y(), pos.z()));
        if (type == null || !expectedBlockId.equals(type.getId())) {
            return;
        }
        BlockHarvestUtils.naturallyRemoveBlock(
                new Vector3i(pos.x(), pos.y(), pos.z()),
                type,
                blocks.getFiller(pos.x(), pos.y(), pos.z()),
                1,
                dropItemId,
                null,
                0,
                section,
                world.getEntityStore().getStore(),
                cs.getStore());
    }

    /**
     * Spawns {@code items}, with their damage, as item entities at the block's bottom centre, as
     * BlockHarvestUtils.spawnDrops does for a broken block. The chunk must be loaded.
     */
    public void drop(BlockPos pos, List<ItemAmount> items) {
        List<ItemStack> dropped = new ArrayList<>(items.size());
        for (ItemAmount a : items) {
            dropped.add(stacks.toStack(a));
        }
        Store<EntityStore> entities = world.getEntityStore().getStore();
        Vector3d at = new Vector3d(pos.x() + 0.5, pos.y(), pos.z() + 0.5);
        entities.addEntities(
                ItemComponent.generateItemDrops(entities, dropped, at, Rotation3f.IDENTITY), AddReason.SPAWN);
    }

    /** {@code s} as a core stack, with the damage its durability shows. */
    ItemAmount toAmount(ItemStack s) {
        return stacks.toAmount(s);
    }

    /** What a player gets with the right tool: breaking drops, else soft drops, else the block's own item. */
    static List<ItemStack> drops(BlockType type) {
        BlockGathering g = type.getGathering();
        BlockBreakingDropType breaking = g == null ? null : g.getBreaking();
        if (breaking != null) {
            return BlockHarvestUtils.getDrops(
                    type, Math.max(1, breaking.getQuantity()), breaking.getItemId(), breaking.getDropListId());
        }
        if (g != null && g.getSoft() != null) {
            return BlockHarvestUtils.getDrops(
                    type, 1, g.getSoft().getItemId(), g.getSoft().getDropListId());
        }
        return BlockHarvestUtils.getDrops(type, 1, null, null);
    }
}
