package dev.hycolony.core.testing;

import dev.hycolony.core.kernel.item.BlockItems;
import dev.hycolony.core.kernel.item.BlockKey;
import dev.hycolony.core.kernel.item.BlockKind;
import dev.hycolony.core.kernel.item.FoodInfo;
import dev.hycolony.core.kernel.item.ItemKey;
import dev.hycolony.core.kernel.item.ToolInfo;
import dev.hycolony.core.kernel.item.ToolType;
import dev.hycolony.core.kernel.port.ItemCatalog;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

public final class FakeCatalog implements ItemCatalog {
    public int defaultMaxStack = 64;
    public final Map<ItemKey, Integer> maxStacks = new HashMap<>();
    /** A block's own item, made by a recipe; {@link #blockItems} sets the other facts. */
    public final Map<BlockKey, ItemKey> itemForBlock = new HashMap<>();

    public final Map<BlockKey, BlockItems> blockItems = new HashMap<>();
    public final Map<BlockKey, BlockKind> kinds = new HashMap<>();
    public final Set<BlockKey> ores = new HashSet<>();
    public final Set<BlockKey> harmful = new HashSet<>();
    public final Set<BlockKey> beds = new HashSet<>();
    public final Set<BlockKey> seats = new HashSet<>();
    /** SOLID blocks that are no good floor (leaves); every other SOLID block is one. */
    public final Set<BlockKey> notGoodFloor = new HashSet<>();

    public final Map<BlockKey, ToolType> toolForBlock = new HashMap<>();
    public final Map<BlockKey, Float> hardness = new HashMap<>();
    public final Map<ItemKey, ToolInfo> tools = new LinkedHashMap<>();
    public final Map<ItemKey, Integer> durability = new HashMap<>();
    public final Map<ItemKey, FoodInfo> foods = new HashMap<>();
    public final Map<ItemKey, ItemKey> cooked = new HashMap<>();

    /** Makes {@code item} a food of {@code nutrition} and {@code tier}; returns it. */
    public ItemKey food(String item, int nutrition, int tier) {
        ItemKey key = new ItemKey(item);
        foods.put(key, new FoodInfo(nutrition, tier, false));
        return key;
    }

    @Override
    public Optional<FoodInfo> food(ItemKey item) {
        return Optional.ofNullable(foods.get(item));
    }

    @Override
    public Optional<ItemKey> cooked(ItemKey item) {
        return Optional.ofNullable(cooked.get(item));
    }

    @Override
    public int maxStack(ItemKey item) {
        return maxStacks.getOrDefault(item, defaultMaxStack);
    }

    /** The block's items as set in {@link #blockItems}, else its own craftable item from {@link #itemForBlock}. */
    @Override
    public BlockItems blockItems(BlockKey block) {
        BlockItems set = blockItems.get(block);
        if (set != null) {
            return set;
        }
        ItemKey own = itemForBlock.get(block);
        return own == null ? BlockItems.NONE : BlockItems.of(own);
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
    public boolean isSeat(BlockKey block) {
        return seats.contains(block);
    }

    /** The foods by id, for a set order. */
    @Override
    public List<ItemKey> foods() {
        return foods.keySet().stream().sorted(Comparator.comparing(ItemKey::id)).toList();
    }

    /** The tools in the order they were put in, as the port promises no order. */
    @Override
    public List<ItemKey> tools() {
        return List.copyOf(tools.keySet());
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
