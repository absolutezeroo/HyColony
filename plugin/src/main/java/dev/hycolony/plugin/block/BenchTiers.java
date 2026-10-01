package dev.hycolony.plugin.block;

import com.hypixel.hytale.builtin.crafting.component.BenchBlock;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.server.core.asset.type.blocktype.config.BlockType;
import com.hypixel.hytale.server.core.modules.block.BlockModule;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.chunk.BlockOperations;
import com.hypixel.hytale.server.core.universe.world.chunk.section.BlockSection;
import com.hypixel.hytale.server.core.universe.world.storage.ChunkStore;
import dev.hycolony.core.kernel.BlockPos;
import java.util.OptionalInt;
import org.joml.Vector3i;

/**
 * Raises a placed bench to a tier the way a player's upgrade ends (CraftingManager.finishTierUpgrade): the
 * {@code BenchBlock} tier level, the {@code Tier<N>} interaction state on its base block, a save mark and the tier
 * listeners (augment blocks). No upgrade items are stored in the bench: breaking it gives back the bench only
 * (plugin-b-api § « Recettes et tables »).
 */
public final class BenchTiers {
    private BenchTiers() {}

    /** The tier of the bench at {@code pos}; empty if the chunk is unloaded or it is no bench. */
    public static OptionalInt get(World world, BlockPos pos) {
        Ref<ChunkStore> section = HytaleSections.section(world, pos);
        if (section == null) {
            return OptionalInt.empty();
        }
        Store<ChunkStore> store = world.getChunkStore().getStore();
        Ref<ChunkStore> entity = BlockModule.getBlockEntity(store, section, pos.x(), pos.y(), pos.z());
        BenchBlock bench = entity == null ? null : store.getComponent(entity, BenchBlock.getComponentType());
        return bench == null ? OptionalInt.empty() : OptionalInt.of(bench.getTierLevel());
    }

    /** True once the bench at {@code pos} has {@code tier}; false if the chunk is unloaded or it is no bench. */
    public static boolean set(World world, BlockPos pos, int tier) {
        Ref<ChunkStore> section = HytaleSections.section(world, pos);
        if (section == null || tier < 1) {
            return false;
        }
        ChunkStore chunks = world.getChunkStore();
        Store<ChunkStore> store = chunks.getStore();
        Ref<ChunkStore> entity = BlockModule.getBlockEntity(store, section, pos.x(), pos.y(), pos.z());
        BenchBlock bench = entity == null ? null : store.getComponent(entity, BenchBlock.getComponentType());
        BlockModule.BlockStateInfo info =
                entity == null ? null : store.getComponent(entity, BlockModule.BlockStateInfo.getComponentType());
        BlockSection blocks = store.getComponent(section, BlockSection.getComponentType());
        if (bench == null || info == null || blocks == null) {
            return false;
        }
        BlockType type = BlockType.getAssetMap().getAsset(blocks.get(pos.x(), pos.y(), pos.z()));
        if (type == null) {
            return false;
        }
        bench.setTierLevel(tier);
        BlockOperations.setBlockInteractionState(
                chunks,
                section,
                pos.x(),
                pos.y(),
                pos.z(),
                BenchBlock.getBaseBlockType(type),
                bench.getTierStateName(),
                true);
        info.markNeedsSaving();
        BenchBlock.notifyTierUpgraded(world, new Vector3i(pos.x(), pos.y(), pos.z()), tier);
        return true;
    }
}
