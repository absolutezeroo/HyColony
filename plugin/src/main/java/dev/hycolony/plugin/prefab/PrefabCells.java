package dev.hycolony.plugin.prefab;

import com.hypixel.hytale.builtin.blockspawner.BlockSpawnerEntry;
import com.hypixel.hytale.builtin.blockspawner.BlockSpawnerTable;
import com.hypixel.hytale.builtin.blockspawner.state.BlockSpawner;
import com.hypixel.hytale.builtin.crafting.component.BenchBlock;
import com.hypixel.hytale.component.Holder;
import com.hypixel.hytale.protocol.BenchType;
import com.hypixel.hytale.server.core.asset.type.blocktype.config.BlockType;
import com.hypixel.hytale.server.core.asset.type.blocktype.config.VariantRotation;
import com.hypixel.hytale.server.core.asset.type.blocktype.config.bench.Bench;
import com.hypixel.hytale.server.core.asset.type.fluid.Fluid;
import com.hypixel.hytale.server.core.modules.block.components.ItemContainerBlock;
import com.hypixel.hytale.server.core.universe.world.storage.ChunkStore;
import dev.hycolony.core.kernel.item.BlockKey;
import dev.hycolony.core.kernel.item.BlockState;
import dev.hycolony.core.kernel.item.Workstation;
import dev.hycolony.plugin.block.HytaleBlockStates;
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

    /** A blueprint state, whether it holds items, and the crafting bench it is (with the prefab's tier), if any. */
    record Resolved(BlockState state, boolean container, Optional<Workstation> workstation) {}

    /**
     * A MineColonies placeholder cell (docs/research/structurize-placeholders.md § 2): an explicit {@code Empty}
     * (minecraft:air, cleared), a solid placeholder (blocksolidsubstitution, filled) or a fluid placeholder
     * (blockfluidsubstitution, flooded).
     */
    enum Marker {
        AIR,
        FILL,
        FLUID
    }

    /** The two placeholder block ids of the id-map. */
    record Placeholders(String solid, String fluid) {}

    /** The cell's marker in a MineColonies blueprint; empty for a block, a fluid-only cell or anything else. */
    static Optional<Marker> marker(int blockId, int fluidId, Placeholders ids) {
        if (blockId == BlockType.EMPTY_ID) {
            return fluidId == 0 ? Optional.of(Marker.AIR) : Optional.empty();
        }
        BlockType type = BlockType.getAssetMap().getAsset(blockId);
        String id = type == null ? "" : type.getId();
        if (id.equals(ids.solid())) {
            return Optional.of(Marker.FILL);
        }
        return id.equals(ids.fluid()) ? Optional.of(Marker.FLUID) : Optional.empty();
    }

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
        String id = HytaleBlockStates.blockKey(type);
        if (id.equals(SPAWNER) && chest != null && isChestSpawner(holder)) {
            id = chest;
        }
        return block(id, rotation).map(r -> new Resolved(r.state(), r.container(), workstation(type, holder)));
    }

    /**
     * The cell's crafting bench: its bench id and the prefab's {@code BenchBlock.TierLevel} (1 when the cell stores
     * none). Only {@code Crafting} benches; processing, diagram and structural benches are out of SP3b-1's scope.
     */
    private static Optional<Workstation> workstation(BlockType type, @Nullable Holder<ChunkStore> holder) {
        Bench bench = type.getBench();
        if (bench == null || bench.getType() != BenchType.Crafting || bench.getId() == null) {
            return Optional.empty();
        }
        BenchBlock stored = holder == null ? null : holder.getComponent(BenchBlock.getComponentType());
        return Optional.of(new Workstation(bench.getId(), Math.max(1, stored == null ? 1 : stored.getTierLevel())));
    }

    private static Optional<Resolved> fluid(int fluidId) {
        Fluid fluid = fluidId == 0 ? null : Fluid.getAssetMap().getAsset(fluidId);
        return fluid == null
                ? Optional.empty()
                : Optional.of(new Resolved(
                        new BlockState(new BlockKey(FLUID_PREFIX + fluid.getId()), 0), false, Optional.empty()));
    }

    /** A block that cannot rotate gets rotation 0: the prefab buffer adds the yaw to every block. */
    private static Optional<Resolved> block(String id, int rotation) {
        BlockType type = BlockType.getAssetMap().getAsset(id);
        if (type == null || id.equals("Empty") || id.equals(SPAWNER) || id.startsWith("Editor_")) {
            return Optional.empty();
        }
        int rot = type.getVariantRotation() == VariantRotation.None ? 0 : rotation;
        return Optional.of(new Resolved(new BlockState(new BlockKey(id), rot), hasContainer(type), Optional.empty()));
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
