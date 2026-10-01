package dev.hycolony.core.testing;

import dev.hycolony.core.kernel.item.BlockKey;
import dev.hycolony.core.kernel.item.BlockKind;
import dev.hycolony.core.kernel.item.ItemKey;
import dev.hycolony.core.kernel.item.ToolInfo;
import dev.hycolony.core.kernel.item.ToolType;
import dev.hycolony.core.kernel.port.ItemCatalog;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

public final class FakeCatalog implements ItemCatalog {
    public int defaultMaxStack = 64;
    public final Map<ItemKey, Integer> maxStacks = new HashMap<>();
    public final Map<BlockKey, ItemKey> itemForBlock = new HashMap<>();
    public final Map<BlockKey, BlockKind> kinds = new HashMap<>();
    public final Set<BlockKey> ores = new HashSet<>();
    public final Set<BlockKey> harmful = new HashSet<>();
    public final Set<BlockKey> beds = new HashSet<>();
    /** SOLID blocks that are no good floor (leaves); every other SOLID block is one. */
    public final Set<BlockKey> notGoodFloor = new HashSet<>();

    public final Map<BlockKey, ToolType> toolForBlock = new HashMap<>();
    public final Map<BlockKey, Float> hardness = new HashMap<>();
    public final Map<ItemKey, ToolInfo> tools = new HashMap<>();
    public final Map<ItemKey, Integer> durability = new HashMap<>();

    @Override
    public int maxStack(ItemKey item) {
        return maxStacks.getOrDefault(item, defaultMaxStack);
    }

    @Override
    public Optional<ItemKey> itemForBlock(BlockKey block) {
        return Optional.ofNullable(itemForBlock.get(block));
    }

    @Override
    public BlockKind kind(BlockKey block) {
        return kinds.getOrDefault(block, BlockKind.SOLID);
    }

    @Override
    public boolean isOre(BlockKey block) {
        return ores.contains(block);
    }

    @Override
    public boolean isGoodFloor(BlockKey block) {
        return kind(block) == BlockKind.SOLID && !notGoodFloor.contains(block);
    }

    @Override
    public boolean isHarmful(BlockKey block) {
        return harmful.contains(block);
    }

    @Override
    public boolean isBed(BlockKey block) {
        return beds.contains(block);
    }

    @Override
    public Optional<ToolType> toolFor(BlockKey block) {
        return Optional.ofNullable(toolForBlock.get(block));
    }

    @Override
    public float hardness(BlockKey block) {
        return hardness.getOrDefault(block, 1.0f);
    }

    @Override
    public Optional<ToolInfo> tool(ItemKey item) {
        return Optional.ofNullable(tools.get(item));
    }

    @Override
    public int durability(ItemKey item) {
        return durability.getOrDefault(item, 0);
    }
}
