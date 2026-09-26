package dev.hycolony.core.colony;

import dev.hycolony.core.construction.Blueprint;
import dev.hycolony.core.construction.BlueprintSource;
import dev.hycolony.core.kernel.BlockPos;
import dev.hycolony.core.kernel.item.BlockKey;
import dev.hycolony.core.kernel.item.BlockKind;
import dev.hycolony.core.kernel.item.BlockState;
import dev.hycolony.core.kernel.item.ItemAmount;
import dev.hycolony.core.kernel.item.ItemKey;
import dev.hycolony.core.kernel.item.ToolInfo;
import dev.hycolony.core.kernel.item.ToolType;
import dev.hycolony.core.kernel.port.ContainerAccess;
import dev.hycolony.core.kernel.port.ItemCatalog;
import dev.hycolony.core.kernel.port.PlayerInventory;
import dev.hycolony.core.kernel.port.WorldBlocks;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/** Ports the construction system needs. {@link #unavailable()} lets the plugin compile before it wires the real ones. */
public record ConstructionPorts(
        ItemCatalog catalog,
        WorldBlocks blocks,
        ContainerAccess containers,
        PlayerInventory playerInventory,
        BlueprintSource blueprints) {

    public static ConstructionPorts unavailable() {
        ItemCatalog catalog = new ItemCatalog() {
            @Override
            public int maxStack(ItemKey item) {
                return 0;
            }

            @Override
            public Optional<ItemKey> itemForBlock(BlockKey block) {
                return Optional.empty();
            }

            @Override
            public BlockKind kind(BlockKey block) {
                return BlockKind.AIR;
            }

            @Override
            public boolean isOre(BlockKey block) {
                return false;
            }

            @Override
            public Optional<ToolType> toolFor(BlockKey block) {
                return Optional.empty();
            }

            @Override
            public float hardness(BlockKey block) {
                return 0f;
            }

            @Override
            public Optional<ToolInfo> tool(ItemKey item) {
                return Optional.empty();
            }

            @Override
            public int durability(ItemKey item) {
                return 0;
            } // Plan B: read the item's max durability
        };
        WorldBlocks blocks = new WorldBlocks() {
            @Override
            public boolean isLoaded(BlockPos pos) {
                return false;
            }

            @Override
            public Optional<BlockState> get(BlockPos pos) {
                return Optional.empty();
            }

            @Override
            public boolean place(BlockPos pos, BlockState state, boolean withContainer) {
                return false;
            }

            @Override
            public List<ItemAmount> breakBlock(BlockPos pos) {
                return List.of();
            }
        };
        ContainerAccess containers = new ContainerAccess() {
            @Override
            public int count(List<BlockPos> containers, ItemKey item) {
                return 0;
            }

            @Override
            public int extract(List<BlockPos> containers, ItemKey item, int max) {
                return 0;
            }

            @Override
            public ItemAmount insert(List<BlockPos> containers, ItemAmount amount) {
                return amount;
            }

            @Override
            public Map<ItemKey, Integer> contents(List<BlockPos> containers) {
                return Map.of();
            }
        };
        PlayerInventory playerInventory = new PlayerInventory() {
            @Override
            public int count(UUID player, ItemKey item) {
                return 0;
            }

            @Override
            public int take(UUID player, ItemKey item, int max) {
                return 0;
            }

            @Override
            public Map<ItemKey, Integer> contents(UUID player) {
                return Map.of();
            }

            @Override
            public ItemAmount give(UUID player, ItemAmount amount) {
                return amount;
            }
        };
        BlueprintSource blueprints = new BlueprintSource() {
            @Override
            public Optional<Blueprint> load(String style, String buildingTypeId, int level, int rotation) {
                return Optional.empty();
            }

            @Override
            public List<String> styles() {
                return List.of();
            }
        };
        return new ConstructionPorts(catalog, blocks, containers, playerInventory, blueprints);
    }
}
