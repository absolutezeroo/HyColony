package dev.hycolony.plugin.block;

import static dev.hycolony.plugin.block.HytaleSections.section;

import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.server.core.asset.type.blocktype.config.BlockType;
import com.hypixel.hytale.server.core.inventory.ItemStack;
import com.hypixel.hytale.server.core.modules.block.BlockModule;
import com.hypixel.hytale.server.core.modules.block.components.ItemContainerBlock;
import com.hypixel.hytale.server.core.modules.interaction.BlockHarvestUtils;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.chunk.section.BlockSection;
import com.hypixel.hytale.server.core.universe.world.storage.ChunkStore;
import com.hypixel.hytale.server.core.util.FillerBlockUtil;
import dev.hycolony.core.kernel.BlockPos;
import dev.hycolony.core.kernel.item.ItemAmount;
import dev.hycolony.plugin.adapter.HytaleBlocks;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Predicate;
import org.joml.Vector3i;
import org.jspecify.annotations.Nullable;

/**
 * Breaks a block for a citizen: a filler cell takes its whole origin block, the origin's container is emptied into
 * the returned drops, and a hut is never broken (it only goes through the hut systems). World thread only; may throw,
 * the world adapter catches.
 */
public final class HytaleBlockBreaker {
    private final World world;
    private final HytaleBlocks drops;
    private final Predicate<BlockType> isHut;

    /** {@code isHut}: true for a hut block (origin or filler cell), which is left in place. */
    public HytaleBlockBreaker(World world, HytaleBlocks drops, Predicate<BlockType> isHut) {
        this.world = world;
        this.drops = drops;
        this.isHut = isHut;
    }

    /** Breaks the block at {@code pos} with the SetBlockSettings {@code settings}; returns what it drops. */
    public List<ItemAmount> breakBlock(BlockPos pos, int settings) {
        Ref<ChunkStore> sec = section(world, pos);
        if (sec == null) {
            return List.of();
        }
        Store<ChunkStore> store = world.getChunkStore().getStore();
        BlockSection blocks = store.getComponent(sec, BlockSection.getComponentType());
        if (blocks == null) {
            return List.of();
        }
        int id = blocks.get(pos.x(), pos.y(), pos.z());
        if (id == BlockType.EMPTY_ID) {
            HytaleSections.clearFluid(store, sec, pos); // a fluid drops nothing
            return List.of();
        }
        BlockType type = breakable(id);
        if (type == null) {
            return List.of();
        }
        // A filler cell belongs to its origin block: the origin holds the container, and the whole block goes.
        int filler = blocks.getFiller(pos.x(), pos.y(), pos.z());
        BlockPos origin = new BlockPos(
                pos.x() - FillerBlockUtil.unpackX(filler),
                pos.y() - FillerBlockUtil.unpackY(filler),
                pos.z() - FillerBlockUtil.unpackZ(filler));
        Ref<ChunkStore> originSec = filler == 0 ? sec : section(world, origin);
        if (originSec == null) {
            return List.of(); // origin unloaded: Hytale would not remove it either, so no drops (no duplication)
        }
        // An orphan filler (its origin is another block) is only cleared: it drops nothing.
        List<ItemStack> out = holds(store, originSec, origin, id) ? takeDrops(type, origin) : List.of();
        BlockHarvestUtils.naturallyRemoveBlock(
                new Vector3i(pos.x(), pos.y(), pos.z()),
                type,
                filler,
                0,
                null,
                null,
                settings,
                sec,
                world.getEntityStore().getStore(),
                store);
        return toAmounts(out);
    }

    /** The type of block {@code id}; null when unknown, empty or a hut. */
    private @Nullable BlockType breakable(int id) {
        BlockType type = BlockType.getAssetMap().getAsset(id);
        if (type == null || type == BlockType.EMPTY || isHut.test(type)) {
            return null; // a hut (origin or filler cell) only goes through the hut systems
        }
        return type;
    }

    /** Whether block {@code id} stands at {@code pos} in section {@code sec}. */
    private static boolean holds(Store<ChunkStore> store, Ref<ChunkStore> sec, BlockPos pos, int id) {
        BlockSection blocks = store.getComponent(sec, BlockSection.getComponentType());
        return blocks != null && blocks.get(pos.x(), pos.y(), pos.z()) == id;
    }

    /** Empties the origin block's container and adds the block's own drops; the removal then drops nothing more. */
    private List<ItemStack> takeDrops(BlockType type, BlockPos origin) {
        List<ItemStack> out = new ArrayList<>();
        ItemContainerBlock container = BlockModule.getComponent(
                ItemContainerBlock.getComponentType(), world, origin.x(), origin.y(), origin.z());
        if (container != null) {
            // Emptied before removal, else the removal system drops it on the ground. No filter: all of it.
            out.addAll(container.getItemContainer().dropAllItemStacks(false));
        }
        out.addAll(HytaleBlocks.drops(type));
        return out;
    }

    /** Converts the non-empty {@code stacks} to amounts. */
    private List<ItemAmount> toAmounts(List<ItemStack> stacks) {
        List<ItemAmount> amounts = new ArrayList<>(stacks.size());
        for (ItemStack s : stacks) {
            if (!ItemStack.isEmpty(s)) {
                amounts.add(drops.toAmount(s));
            }
        }
        return amounts;
    }
}
