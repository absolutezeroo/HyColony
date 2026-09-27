package dev.hycolony.plugin.prefab;

import com.hypixel.hytale.builtin.blockspawner.BlockSpawnerEntry;
import com.hypixel.hytale.builtin.blockspawner.BlockSpawnerTable;
import com.hypixel.hytale.builtin.blockspawner.state.BlockSpawner;
import com.hypixel.hytale.component.Holder;
import com.hypixel.hytale.server.core.asset.type.blocktype.config.BlockType;
import com.hypixel.hytale.server.core.asset.type.blocktype.config.VariantRotation;
import com.hypixel.hytale.server.core.asset.type.fluid.Fluid;
import com.hypixel.hytale.server.core.modules.block.components.ItemContainerBlock;
import com.hypixel.hytale.server.core.universe.world.storage.ChunkStore;
import dev.hycolony.core.kernel.item.BlockKey;
import dev.hycolony.core.kernel.item.BlockState;
import dev.hycolony.plugin.adapter.HytaleWorldBlocks;
import java.util.Optional;
import javax.annotation.Nullable;

/**
 * What one prefab cell becomes in a blueprint (the per-cell rules of HytaleBlueprintSource). {@code Empty},
 * {@code Editor_*} and spawners are skipped, except that a chest spawner (a spawner table with a container block)
 * becomes the given empty chest: vanilla villages never place a chest directly, and the chests the builder places
 * become the building's racks. The chest keeps the spawner's rotation (the spawner's own rotation mode is INHERIT)
 * and gets no loot.
 */
final class PrefabCells {
    private static final String SPAWNER = "Block_Spawner_Block";
    private static final String FLUID_PREFIX = "~fluid:";

    /** A blueprint state and whether it holds items. */
    record Resolved(BlockState state, boolean container) {}

    private PrefabCells() {}

    /**
     * The cell's blueprint state, or empty when it is skipped: air without fluid (a fluid-only cell becomes
     * {@code ~fluid:<FluidKey>}), an unknown block, {@code Empty}, {@code Editor_*}, or a spawner unless {@code chest}
     * is set and it is a chest spawner. A state id is normalised to its default state, like HytaleWorldBlocks.get.
     */
    static Optional<Resolved> resolve(
            int blockId, @Nullable Holder<ChunkStore> holder, int rotation, int fluidId, @Nullable String chest) {
        if (blockId == BlockType.EMPTY_ID) {
            return fluid(fluidId);
        }
        BlockType type = BlockType.getAssetMap().getAsset(blockId);
        if (type == null) {
            return Optional.empty();
        }
        String id = HytaleWorldBlocks.blockKey(type);
        if (id.equals(SPAWNER) && chest != null && isChestSpawner(holder)) {
            id = chest;
        }
        return block(id, rotation);
    }

    private static Optional<Resolved> fluid(int fluidId) {
        Fluid fluid = fluidId == 0 ? null : Fluid.getAssetMap().getAsset(fluidId);
        return fluid == null
                ? Optional.empty()
                : Optional.of(new Resolved(new BlockState(new BlockKey(FLUID_PREFIX + fluid.getId()), 0), false));
    }

    /** A block that cannot rotate gets rotation 0: the prefab buffer adds the yaw to every block. */
    private static Optional<Resolved> block(String id, int rotation) {
        BlockType type = BlockType.getAssetMap().getAsset(id);
        if (type == null || id.equals("Empty") || id.equals(SPAWNER) || id.startsWith("Editor_")) {
            return Optional.empty();
        }
        int rot = type.getVariantRotation() == VariantRotation.None ? 0 : rotation;
        return Optional.of(new Resolved(new BlockState(new BlockKey(id), rot), hasContainer(type)));
    }

    /** Whether one of the spawner's table entries is a block with an item container (a loot chest spawner). */
    private static boolean isChestSpawner(@Nullable Holder<ChunkStore> holder) {
        BlockSpawner spawner = holder == null ? null : holder.getComponent(BlockSpawner.getComponentType());
        String tableId = spawner == null ? null : spawner.getBlockSpawnerId();
        BlockSpawnerTable table =
                tableId == null ? null : BlockSpawnerTable.getAssetMap().getAsset(tableId);
        if (table == null) {
            return false;
        }
        for (BlockSpawnerEntry e : table.getEntries().internalKeys()) {
            String name = e.getBlockName();
            BlockType type = name == null ? null : BlockType.getAssetMap().getAsset(name);
            if (type != null && hasContainer(type)) {
                return true;
            }
        }
        return false;
    }

    private static boolean hasContainer(BlockType type) {
        Holder<ChunkStore> entity = type.getBlockEntity();
        return entity != null && entity.getComponent(ItemContainerBlock.getComponentType()) != null;
    }
}
