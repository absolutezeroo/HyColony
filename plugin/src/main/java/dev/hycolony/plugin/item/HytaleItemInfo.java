package dev.hycolony.plugin.item;

import com.hypixel.hytale.server.core.asset.type.item.config.Item;
import com.hypixel.hytale.server.core.asset.type.item.config.ItemTool;
import com.hypixel.hytale.server.core.asset.type.item.config.ItemToolSpec;
import dev.hycolony.core.kernel.item.ToolInfo;
import dev.hycolony.core.kernel.item.ToolScale;
import dev.hycolony.core.kernel.item.ToolType;
import java.util.Map;
import java.util.Optional;
import org.jspecify.annotations.Nullable;

/**
 * What the item catalog knows of a Hytale item: its max stack, its tool type, level and speed, and its uses before it
 * breaks: a tool's blocks, an armour piece's hits (0 = unbreakable).
 */
public record HytaleItemInfo(int maxStack, Optional<ToolInfo> tool, int durability) {
    /** An item Hytale does not know: a stack of 1, no tool. */
    public static final HytaleItemInfo UNKNOWN = new HytaleItemInfo(1, Optional.empty(), 0);

    /** Item {@code id}'s info: a hoe at its {@code hoeLevels} level, else what its Hytale asset says. */
    public static HytaleItemInfo of(String id, Map<String, Integer> hoeLevels) {
        return hoeLevels.containsKey(id) ? hoe(id, hoeLevels.get(id)) : item(id);
    }

    /**
     * A hoe: tool type HOE at its id-map level, speed 1, and one use per tilled block (Hoe_Till's
     * AdjustHeldItemDurability -1), so its uses are its max durability.
     */
    private static HytaleItemInfo hoe(String id, int level) {
        Item item = Item.getAssetMap().getAsset(id);
        if (item == null) {
            return UNKNOWN;
        }
        return new HytaleItemInfo(
                Math.max(1, item.getMaxStack()), Optional.of(new ToolInfo(ToolType.HOE, level, 1f)), (int)
                        item.getMaxDurability());
    }

    private static HytaleItemInfo item(String id) {
        Item item = Item.getAssetMap().getAsset(id);
        if (item == null) {
            return UNKNOWN;
        }
        int maxStack = Math.max(1, item.getMaxStack());
        ItemTool tool = item.getTool();
        ToolType type = tool == null ? null : toolType(item);
        if (tool == null || type == null) {
            return new HytaleItemInfo(maxStack, Optional.empty(), armorHits(item));
        }
        return tool(item, tool, type, maxStack);
    }

    /**
     * An armour piece's uses: the hits it takes before it breaks, each costing its DurabilityLossOnHit of its
     * MaxDurability, as DamageSystems.DamageArmor wears a player's (100 / 0.5 = 200 for vanilla iron); 0 for anything
     * else or an unbreakable piece.
     */
    private static int armorHits(Item item) {
        double max = item.getMaxDurability();
        if (item.getArmor() == null || max <= 0) {
            return 0;
        }
        double perHit = item.getDurabilityLossOnHit();
        return perHit > 0 ? (int) Math.ceil(max / perHit) : (int) max;
    }

    /**
     * A tool of {@code type}: its level and speed from its spec for its own gather type, against the bare hand's, and
     * its uses ({@link #durability}).
     */
    private static HytaleItemInfo tool(Item item, ItemTool tool, ToolType type, int maxStack) {
        String gather = switch (type) {
            case PICKAXE -> "Rocks";
            case AXE -> "Woods";
            case SHOVEL, HOE -> "Soils";
        };
        ItemToolSpec spec = spec(tool, gather);
        ItemToolSpec unarmed = ItemToolSpec.getAssetMap().getAsset(gather);
        float power = spec == null ? 0f : spec.getPower();
        float speed = ToolScale.speed(power, unarmed == null ? 0f : unarmed.getPower());
        int level = spec == null ? 0 : ToolScale.level(spec.getQuality());
        return new HytaleItemInfo(
                maxStack, Optional.of(new ToolInfo(type, level, speed)), durability(item, tool, power));
    }

    /** The tool type {@code item}'s player animations name; null for another. */
    private static @Nullable ToolType toolType(Item item) {
        return switch (String.valueOf(item.getPlayerAnimationsId())) {
            case "Pickaxe" -> ToolType.PICKAXE;
            case "Hatchet" -> ToolType.AXE;
            case "Shovel" -> ToolType.SHOVEL;
            default -> null;
        };
    }

    /** {@code tool}'s spec for its own {@code gather} type; null if it lists none. */
    private static @Nullable ItemToolSpec spec(ItemTool tool, String gather) {
        if (tool.getSpecs() == null) {
            return null;
        }
        for (ItemToolSpec s : tool.getSpecs()) {
            if (gather.equals(s.getGatherType())) {
                return s;
            }
        }
        return null;
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
}
