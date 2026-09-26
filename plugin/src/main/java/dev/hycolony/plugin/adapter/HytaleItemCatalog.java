package dev.hycolony.plugin.adapter;

import com.hypixel.hytale.logger.HytaleLogger;
import com.hypixel.hytale.protocol.BlockMaterial;
import com.hypixel.hytale.server.core.asset.type.blocktype.config.BlockBreakingDropType;
import com.hypixel.hytale.server.core.asset.type.blocktype.config.BlockGathering;
import com.hypixel.hytale.server.core.asset.type.blocktype.config.BlockType;
import com.hypixel.hytale.server.core.asset.type.item.config.Item;
import com.hypixel.hytale.server.core.asset.type.item.config.ItemTool;
import com.hypixel.hytale.server.core.asset.type.item.config.ItemToolSpec;
import dev.hycolony.core.kernel.item.BlockKey;
import dev.hycolony.core.kernel.item.BlockKind;
import dev.hycolony.core.kernel.item.ItemKey;
import dev.hycolony.core.kernel.item.ToolInfo;
import dev.hycolony.core.kernel.item.ToolType;
import dev.hycolony.core.kernel.port.ItemCatalog;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.logging.Level;

/**
 * ItemCatalog over the Hytale asset maps (cheat sheet § 2 and § 1 "Hardness"). Results are cached per key; the cache
 * is not cleared on an asset reload (restart after changing assets). World thread only.
 *
 * <p>Hytale has no hardness and no MineColonies tool levels, so these are mapped:
 * <ul>
 *   <li>tool type from the block's {@code GatherType}: Rocks, VolcanicRocks, Ore* → PICKAXE; Woods, SoftWoods → AXE;
 *       Soils → SHOVEL; anything else needs no tool;</li>
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
    private static final float MIN_HARDNESS = 0.05f;
    private static final float MAX_HARDNESS = 3f; // ponytail: heuristic, replace by a table if balance is off
    private static final BlockInfo UNKNOWN_BLOCK =
            new BlockInfo(BlockKind.UNBREAKABLE, Optional.empty(), false, Optional.empty(), 1f);
    private static final BlockInfo FLUID =
            new BlockInfo(BlockKind.FLUID, Optional.empty(), false, Optional.empty(), 0f);
    private static final BlockInfo AIR = new BlockInfo(BlockKind.AIR, Optional.empty(), false, Optional.empty(), 0f);
    private static final ItemInfo UNKNOWN_ITEM = new ItemInfo(1, Optional.empty(), 0);

    private record BlockInfo(
            BlockKind kind, Optional<ItemKey> item, boolean ore, Optional<ToolType> tool, float hardness) {}

    private record ItemInfo(int maxStack, Optional<ToolInfo> tool, int durability) {}

    private final Map<BlockKey, BlockInfo> blocks = new HashMap<>();
    private final Map<ItemKey, ItemInfo> items = new HashMap<>();
    private final Set<String> hutBlockIds;
    private boolean warned;

    /** {@code hutBlockIds}: the id-map's hut block ids. */
    public HytaleItemCatalog(Set<String> hutBlockIds) {
        this.hutBlockIds = Set.copyOf(hutBlockIds);
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
                info = computeItem(key.id());
            } catch (RuntimeException e) {
                fail(key.id(), e);
                info = UNKNOWN_ITEM;
            }
            items.put(key, info);
        }
        return info;
    }

    private BlockInfo computeBlock(String id) {
        if (id.startsWith(HytaleWorldBlocks.FLUID_PREFIX)) {
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
        Item item = type.getItem();
        Optional<ItemKey> itemKey = item == null ? Optional.empty() : Optional.of(new ItemKey(item.getId()));
        BlockGathering g = type.getGathering();
        BlockBreakingDropType breaking = g == null ? null : g.getBreaking();
        String gather = breaking == null ? null : breaking.getGatherType();
        if (g == null || "Unbreakable".equals(gather) || hutBlockIds.contains(type.getId())) {
            return new BlockInfo(BlockKind.UNBREAKABLE, itemKey, false, Optional.empty(), 1f);
        }
        BlockKind kind = type.getMaterial() == BlockMaterial.Empty ? BlockKind.NON_SOLID : BlockKind.SOLID;
        float hardness = MIN_HARDNESS; // soft or harvest-only blocks break in one hit
        if (gather != null) {
            ItemToolSpec unarmed = ItemToolSpec.getAssetMap().getAsset(gather);
            hardness = unarmed == null || unarmed.getPower() <= 0
                    ? 1f
                    : Math.clamp(MIN_HARDNESS / unarmed.getPower(), MIN_HARDNESS, MAX_HARDNESS);
        }
        return new BlockInfo(
                kind,
                itemKey,
                gather != null && gather.startsWith("Ore"),
                Optional.ofNullable(toolType(gather)),
                hardness);
    }

    private static ToolType toolType(String gather) {
        if (gather == null) {
            return null;
        }
        if (gather.equals("Rocks") || gather.equals("VolcanicRocks") || gather.startsWith("Ore")) {
            return ToolType.PICKAXE;
        }
        return switch (gather) {
            case "Woods", "SoftWoods" -> ToolType.AXE;
            case "Soils" -> ToolType.SHOVEL;
            default -> null;
        };
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
            case SHOVEL -> "Soils";
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
        float speed = power <= 0 ? 1f : unarmed == null || unarmed.getPower() <= 0 ? power : power / unarmed.getPower();
        int level = spec == null ? 0 : Math.max(0, spec.getQuality() - 1);
        return new ItemInfo(maxStack, Optional.of(new ToolInfo(type, level, speed)), durability(item, tool, power));
    }

    /** Blocks of the tool's own gather type mined before it breaks; 0 = unbreakable. */
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
        if (max <= 0 || loss <= 0) {
            return 0;
        }
        double hitsPerBlock = power > 0 ? Math.ceil(1 / power) : 1;
        return Math.max(1, (int) (max / (loss * hitsPerBlock)));
    }

    private void fail(String id, RuntimeException e) {
        LOG.at(warned ? Level.FINE : Level.WARNING).withCause(e).log("ItemCatalog lookup failed for %s", id);
        warned = true;
    }
}
