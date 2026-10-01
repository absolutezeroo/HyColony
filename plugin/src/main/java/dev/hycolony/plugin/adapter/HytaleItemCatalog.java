package dev.hycolony.plugin.adapter;

import com.hypixel.hytale.logger.HytaleLogger;
import com.hypixel.hytale.protocol.BlockMaterial;
import com.hypixel.hytale.protocol.DrawType;
import com.hypixel.hytale.server.core.asset.type.blocktype.config.BlockBreakingDropType;
import com.hypixel.hytale.server.core.asset.type.blocktype.config.BlockGathering;
import com.hypixel.hytale.server.core.asset.type.blocktype.config.BlockType;
import com.hypixel.hytale.server.core.asset.type.fluid.Fluid;
import com.hypixel.hytale.server.core.asset.type.item.config.Item;
import com.hypixel.hytale.server.core.asset.type.item.config.ItemTool;
import com.hypixel.hytale.server.core.asset.type.item.config.ItemToolSpec;
import dev.hycolony.core.kernel.item.BlockKey;
import dev.hycolony.core.kernel.item.BlockKind;
import dev.hycolony.core.kernel.item.FoodInfo;
import dev.hycolony.core.kernel.item.ItemKey;
import dev.hycolony.core.kernel.item.ToolInfo;
import dev.hycolony.core.kernel.item.ToolScale;
import dev.hycolony.core.kernel.item.ToolType;
import dev.hycolony.core.kernel.port.ItemCatalog;
import dev.hycolony.plugin.block.HytaleBlockStates;
import dev.hycolony.plugin.food.FoodIds;
import dev.hycolony.plugin.food.HytaleFoods;
import dev.hycolony.plugin.item.HytaleStacks;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.logging.Level;
import org.jspecify.annotations.Nullable;

/**
 * ItemCatalog over the Hytale asset maps (cheat sheet § 2 and § 1 "Hardness"). Results are cached per key; the cache
 * is not cleared on an asset reload (restart after changing assets). World thread only.
 *
 * <p>Hytale has no hardness and no MineColonies tool levels, so these are mapped:
 * <ul>
 *   <li>tool type from the block's {@code GatherType}: Rocks, VolcanicRocks, GoblinMetal, Ore* → PICKAXE; Woods,
 *       SoftWoods → AXE; Soils → SHOVEL; anything else needs no tool;</li>
 *   <li>tool level = {@code max(0, Quality - 1)} of the tool's spec for its own gather type (pickaxe → Rocks,
 *       axe → Woods, shovel → Soils). Vanilla pickaxes start at Quality 1 (Wood, Crude, Scrap → level 0; Copper 1;
 *       Iron 2; Cobalt, Thorium 3; Adamantite 4; Mithril, Onyxium 5); hatchets and shovels have no Quality, so they
 *       are all level 0. The lowest tier is therefore always 0, as the core requires;</li>
 *   <li>hardness = {@code 0.05 / unarmedPower}, clamped to [0.05, 3], and tool speed = {@code power / unarmedPower}
 *       for the same gather type, so {@code hardness / speed = 0.05 / power}: mining time follows Hytale's hits per
 *       block;</li>
 *   <li>durability = blocks of its own gather type mined before breaking:
 *       {@code maxDurability / (lossPerHit * ceil(1 / power))}.</li>
 * </ul>
 * Hut blocks are UNBREAKABLE, so the builder never mines or builds over a hut, filler cells included (a filler cell
 * reports its origin's block, see {@link HytaleWorldBlocks}).
 */
public final class HytaleItemCatalog implements ItemCatalog {
    private static final HytaleLogger LOG = HytaleLogger.forEnclosingClass();
    private static final BlockInfo UNKNOWN_BLOCK =
            new BlockInfo(BlockKind.UNBREAKABLE, Optional.empty(), false, Optional.empty(), 1f, false, false, false);
    private static final BlockInfo FLUID =
            new BlockInfo(BlockKind.FLUID, Optional.empty(), false, Optional.empty(), 0f, false, false, false);
    private static final BlockInfo AIR =
            new BlockInfo(BlockKind.AIR, Optional.empty(), false, Optional.empty(), 0f, false, false, false);
    private static final ItemInfo UNKNOWN_ITEM = new ItemInfo(1, Optional.empty(), 0);

    private record BlockInfo(
            BlockKind kind,
            Optional<ItemKey> item,
            boolean ore,
            Optional<ToolType> tool,
            float hardness,
            boolean harmful,
            boolean bed,
            boolean seat) {}

    private record ItemInfo(int maxStack, Optional<ToolInfo> tool, int durability) {}

    private final Map<BlockKey, BlockInfo> blocks = new HashMap<>();
    private final Map<ItemKey, ItemInfo> items = new HashMap<>();
    private final Map<BlockKey, Boolean> goodFloors = new HashMap<>();
    private final Set<String> hutBlockIds;
    /** The id-map's hoes and their tool level: Hytale hoes have no tool spec to map (they till by interaction). */
    private final Map<String, Integer> hoeLevels;

    private final HytaleStacks stacks = new HytaleStacks(this::durability);
    private final HytaleFoods foods;
    private boolean warned;

    /**
     * {@code hutBlockIds}: the id-map's hut block ids; {@code hoeLevels}: its hoes and their tool level; {@code foods}:
     * its food table and cooking bench.
     */
    public HytaleItemCatalog(Set<String> hutBlockIds, Map<String, Integer> hoeLevels, FoodIds foods) {
        this.hutBlockIds = Set.copyOf(hutBlockIds);
        this.hoeLevels = Map.copyOf(hoeLevels);
        this.foods = new HytaleFoods(foods);
    }

    /** The id-map's food table (Hytale has no nutrition, see {@link HytaleFoods}). */
    @Override
    public Optional<FoodInfo> food(ItemKey item) {
        return foods.food(item);
    }

    @Override
    public List<ItemKey> foods() {
        return foods.foods();
    }

    /** The food table this catalog reads, shared with the cooking catalog. */
    public HytaleFoods foodTable() {
        return foods;
    }

    /** The cooking bench's result for {@code item} (MC the furnace's smelting result). */
    @Override
    public Optional<ItemKey> cooked(ItemKey item) {
        return foods.cooked(item);
    }

    /** The stack conversion that turns a tool's damage into Hytale durability with this catalog's durabilities. */
    public HytaleStacks stacks() {
        return stacks;
    }

    @Override
    public int maxStack(ItemKey item) {
        return item(item).maxStack();
    }

    @Override
    public Optional<ItemKey> itemForBlock(BlockKey block) {
        return block(block).item();
    }

    @Override
    public BlockKind kind(BlockKey block) {
        return block(block).kind();
    }

    @Override
    public boolean isOre(BlockKey block) {
        return block(block).ore();
    }

    /**
     * A solid block drawn as a full cube ({@code Cube}, or {@code CubeWithModel} as ores are), leaves excluded
     * (Structurize {@code unsuitable_solid_for_placeholder}): vanilla leaves are models of group {@code Leaves}, and
     * tilled soil is a cube ({@code Template_Soil}), as Structurize's {@code good_solid_for_placeholder} wants. A state
     * variant is judged by its own block type (a slab's {@code Full} state is a cube); an unknown block or a fluid is
     * no good floor. Cached per key.
     *
     * <p>Deviation from MC: Structurize tests the collision shape ({@code isGoodFullBlock}); Hytale has no such shape
     * on the server, so the draw type and material stand for it.
     */
    @Override
    public boolean isGoodFloor(BlockKey block) {
        return goodFloors.computeIfAbsent(block, k -> {
            try {
                BlockType type = BlockType.getAssetMap().getAsset(k.id());
                return type != null
                        && !type.isUnknown()
                        && type.getMaterial() == BlockMaterial.Solid
                        && (type.getDrawType() == DrawType.Cube || type.getDrawType() == DrawType.CubeWithModel)
                        && !"Leaves".equals(type.getGroup());
            } catch (RuntimeException e) {
                fail(k.id(), e);
                return false;
            }
        });
    }

    /**
     * A fluid or block with {@code DamageToEntities} or a collision interaction: vanilla lava, fire, unlit campfires,
     * braziers and cacti hurt through their {@code Collision} interaction (their damage is 0), which
     * {@link Fluid#isTrigger()} and {@link BlockType#isTrigger()} report, the pair the collision module checks
     * ({@code CollisionConfig}). A few harmless triggers (traps, slowing seaweed) are avoided too. A fluid key absent
     * from the asset map counts as harmful; an unknown fluid read from a chunk is an {@code UNKNOWN} clone with no
     * damage and no interaction, so it does not.
     */
    @Override
    public boolean isHarmful(BlockKey block) {
        String id = block.id();
        if (!id.startsWith(HytaleBlockStates.FLUID_PREFIX)) {
            return block(block).harmful();
        }
        try {
            Fluid fluid = Fluid.getAssetMap().getAsset(id.substring(HytaleBlockStates.FLUID_PREFIX.length()));
            return fluid == null || fluid.getDamageToEntities() > 0 || fluid.isTrigger();
        } catch (RuntimeException e) {
            fail(id, e);
            return true;
        }
    }

    /** BlockType.getBeds() is non-null for every bed (vanilla and HyVanilla); an unknown block or a fluid is none. */
    @Override
    public boolean isBed(BlockKey block) {
        return block(block).bed();
    }

    /** BlockType.getSeats() is non-null for every seat (32 vanilla chairs, stools, benches, sp4b-hytale-food § 6). */
    @Override
    public boolean isSeat(BlockKey block) {
        return block(block).seat();
    }

    @Override
    public Optional<ToolType> toolFor(BlockKey block) {
        return block(block).tool();
    }

    @Override
    public float hardness(BlockKey block) {
        return block(block).hardness();
    }

    @Override
    public Optional<ToolInfo> tool(ItemKey item) {
        return item(item).tool();
    }

    @Override
    public int durability(ItemKey item) {
        return item(item).durability();
    }

    private BlockInfo block(BlockKey key) {
        BlockInfo info = blocks.get(key);
        if (info == null) {
            try {
                info = computeBlock(key.id());
            } catch (RuntimeException e) {
                fail(key.id(), e);
                info = UNKNOWN_BLOCK;
            }
            blocks.put(key, info);
        }
        return info;
    }

    private ItemInfo item(ItemKey key) {
        ItemInfo info = items.get(key);
        if (info == null) {
            try {
                info = hoeLevels.containsKey(key.id()) ? computeHoe(key.id()) : computeItem(key.id());
            } catch (RuntimeException e) {
                fail(key.id(), e);
                info = UNKNOWN_ITEM;
            }
            items.put(key, info);
        }
        return info;
    }

    private BlockInfo computeBlock(String id) {
        if (id.startsWith(HytaleBlockStates.FLUID_PREFIX)) {
            return FLUID;
        }
        if (BlockType.EMPTY_KEY.equals(id)) {
            return AIR;
        }
        BlockType type = BlockType.getAssetMap().getAsset(id);
        if (type != null && id.startsWith("*") && type.getDefaultStateKey() != null) {
            type = BlockType.getAssetMap().getAsset(type.getDefaultStateKey()); // a state variant is its base block
        }
        if (type == null || type.isUnknown()) {
            return UNKNOWN_BLOCK;
        }
        if (type == BlockType.EMPTY) {
            return AIR;
        }
        boolean harmful = type.getDamageToEntities() > 0 || type.isTrigger();
        boolean bed = type.getBeds() != null; // every bed has sleeping points (BlockMountAPI), vanilla and HyVanilla
        boolean seat = type.getSeats() != null; // chairs, stools, benches (BlockMountAPI takes a seat first)
        Item item = type.getItem();
        Optional<ItemKey> itemKey = item == null ? Optional.empty() : Optional.of(new ItemKey(item.getId()));
        BlockGathering g = type.getGathering();
        BlockBreakingDropType breaking = g == null ? null : g.getBreaking();
        String gather = breaking == null ? null : breaking.getGatherType();
        if (g == null || "Unbreakable".equals(gather) || hutBlockIds.contains(type.getId())) {
            return new BlockInfo(BlockKind.UNBREAKABLE, itemKey, false, Optional.empty(), 1f, harmful, bed, seat);
        }
        BlockKind kind = type.getMaterial() == BlockMaterial.Empty ? BlockKind.NON_SOLID : BlockKind.SOLID;
        return new BlockInfo(
                kind,
                itemKey,
                gather != null && gather.startsWith("Ore"),
                Optional.ofNullable(toolType(gather)),
                hardness(gather),
                harmful,
                bed,
                seat);
    }

    /** The block's hardness from its gather type's unarmed power ({@link ToolScale#hardness}). */
    private static float hardness(@Nullable String gather) {
        if (gather == null) {
            return ToolScale.MIN_HARDNESS;
        }
        ItemToolSpec unarmed = ItemToolSpec.getAssetMap().getAsset(gather);
        return ToolScale.hardness(unarmed == null ? 0f : unarmed.getPower());
    }

    private static @Nullable ToolType toolType(@Nullable String gather) {
        if (gather == null) {
            return null;
        }
        if (gather.startsWith("Ore")) {
            return ToolType.PICKAXE;
        }
        return switch (gather) {
            // Not "Metals": every U7 tool lists it IsIncorrect (power 0.001), so no tool is the right one.
            case "Rocks", "VolcanicRocks", "GoblinMetal" -> ToolType.PICKAXE;
            case "Woods", "SoftWoods" -> ToolType.AXE;
            case "Soils" -> ToolType.SHOVEL;
            default -> null;
        };
    }

    /**
     * A hoe: tool type HOE at its id-map level, speed 1, and one use per tilled block (Hoe_Till's
     * AdjustHeldItemDurability -1), so its uses are its max durability.
     */
    private ItemInfo computeHoe(String id) {
        Item item = Item.getAssetMap().getAsset(id);
        if (item == null) {
            return UNKNOWN_ITEM;
        }
        return new ItemInfo(
                Math.max(1, item.getMaxStack()),
                Optional.of(new ToolInfo(ToolType.HOE, hoeLevels.getOrDefault(id, 0), 1f)),
                (int) item.getMaxDurability());
    }

    private static ItemInfo computeItem(String id) {
        Item item = Item.getAssetMap().getAsset(id);
        if (item == null) {
            return UNKNOWN_ITEM;
        }
        int maxStack = Math.max(1, item.getMaxStack());
        ItemTool tool = item.getTool();
        ToolType type = tool == null
                ? null
                : switch (String.valueOf(item.getPlayerAnimationsId())) {
                    case "Pickaxe" -> ToolType.PICKAXE;
                    case "Hatchet" -> ToolType.AXE;
                    case "Shovel" -> ToolType.SHOVEL;
                    default -> null;
                };
        if (type == null) {
            return new ItemInfo(maxStack, Optional.empty(), 0);
        }
        String gather = switch (type) {
            case PICKAXE -> "Rocks";
            case AXE -> "Woods";
            case SHOVEL, HOE -> "Soils";
        };
        ItemToolSpec spec = null;
        if (tool.getSpecs() != null) {
            for (ItemToolSpec s : tool.getSpecs()) {
                if (gather.equals(s.getGatherType())) {
                    spec = s;
                    break;
                }
            }
        }
        ItemToolSpec unarmed = ItemToolSpec.getAssetMap().getAsset(gather);
        float power = spec == null ? 0f : spec.getPower();
        float speed = ToolScale.speed(power, unarmed == null ? 0f : unarmed.getPower());
        int level = spec == null ? 0 : ToolScale.level(spec.getQuality());
        return new ItemInfo(maxStack, Optional.of(new ToolInfo(type, level, speed)), durability(item, tool, power));
    }

    /** Blocks of the tool's own gather type mined before it breaks ({@link ToolScale#uses}); 0 = unbreakable. */
    private static int durability(Item item, ItemTool tool, float power) {
        double max = item.getMaxDurability();
        double loss = item.getDurabilityLossOnHit(); // Hytale's fallback when no block set matches
        ItemTool.DurabilityLossBlockTypes[] perSet = tool.getDurabilityLossBlockTypes();
        if (perSet != null && perSet.length > 0) {
            loss = 0; // vanilla tools list one entry (Stone, Rock, Ores, Soil, Wood): take the worst listed
            for (ItemTool.DurabilityLossBlockTypes t : perSet) {
                loss = Math.max(loss, t.getDurabilityLossOnHit());
            }
        }
        return ToolScale.uses(max, loss, power);
    }

    private void fail(String id, RuntimeException e) {
        LOG.at(warned ? Level.FINE : Level.WARNING).withCause(e).log("ItemCatalog lookup failed for %s", id);
        warned = true;
    }
}
