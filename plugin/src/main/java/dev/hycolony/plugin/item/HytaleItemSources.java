package dev.hycolony.plugin.item;

import com.hypixel.hytale.server.core.asset.type.blocktype.config.BlockBreakingDropType;
import com.hypixel.hytale.server.core.asset.type.blocktype.config.BlockGathering;
import com.hypixel.hytale.server.core.asset.type.blocktype.config.BlockType;
import com.hypixel.hytale.server.core.asset.type.blocktype.config.HarvestingDropType;
import com.hypixel.hytale.server.core.asset.type.blocktype.config.SoftBlockDropType;
import com.hypixel.hytale.server.core.asset.type.item.config.CraftingRecipe;
import com.hypixel.hytale.server.core.asset.type.item.config.Item;
import com.hypixel.hytale.server.core.asset.type.item.config.ItemDrop;
import com.hypixel.hytale.server.core.asset.type.item.config.ItemDropList;
import com.hypixel.hytale.server.core.inventory.MaterialQuantity;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Set;
import org.jspecify.annotations.Nullable;

/**
 * Where a survival player can get an item, as far as the loaded assets tell: a recipe or HyDomum's cutter makes it,
 * or breaking or harvesting some block, or some drop list (mobs, loot), gives it (BlockHarvestUtils.getDrops: a drop
 * without {@code ItemId} nor {@code DropList} gives the block's own item). World generation is not read, so a block
 * that only a structure holds still counts through its own break. Each index is read once, at its first use. World
 * thread only.
 */
public final class HytaleItemSources {
    /**
     * A HyDomum variant id, {@code <template>__<material>...} (dev.hydomum.api.VariantKey.blockTypeKey): the
     * architect's cutter makes it from its materials, outside the recipe assets.
     */
    private static final String VARIANT = "__";

    private @Nullable Set<String> crafted;
    private @Nullable Set<String> dropped;

    /** Whether a recipe, HyDomum's cutter, a block's break or harvest, or a drop list gives {@code itemId}. */
    public boolean hasSource(String itemId) {
        return itemId.contains(VARIANT)
                || crafted().contains(itemId)
                || dropped().contains(itemId);
    }

    private Set<String> crafted() {
        if (crafted == null) {
            Set<String> out = new HashSet<>();
            for (CraftingRecipe r : CraftingRecipe.getAssetMap().getAssetMap().values()) {
                if (r == null) {
                    continue;
                }
                addItem(out, r.getPrimaryOutput());
                for (MaterialQuantity m : r.getOutputs() == null ? new MaterialQuantity[0] : r.getOutputs()) {
                    addItem(out, m);
                }
            }
            crafted = out;
        }
        return crafted;
    }

    private static void addItem(Set<String> out, @Nullable MaterialQuantity m) {
        if (m != null && m.getItemId() != null) {
            out.add(m.getItemId());
        }
    }

    private Set<String> dropped() {
        if (dropped == null) {
            Set<String> out = new HashSet<>();
            BlockType.getAssetMap().getAssetMap().values().forEach(type -> addBreakDrops(out, type));
            ItemDropList.getAssetMap().getAssetMap().values().forEach(list -> addListDrops(out, list));
            dropped = out;
        }
        return dropped;
    }

    /**
     * What breaking {@code type} gives by its {@code Breaking}, else its {@code Soft} drop, plus its {@code Harvest}
     * and its tool-specific drops (shears on leaves); nothing for a block without gathering.
     */
    private static void addBreakDrops(Set<String> out, @Nullable BlockType type) {
        if (type == null || type.getGathering() == null) {
            return;
        }
        BlockGathering g = type.getGathering();
        BlockBreakingDropType breaking = g.getBreaking();
        SoftBlockDropType soft = g.getSoft();
        if (breaking != null) {
            addDrop(out, type, breaking.getItemId(), breaking.getDropListId());
        } else if (soft != null) {
            addDrop(out, type, soft.getItemId(), soft.getDropListId());
        }
        HarvestingDropType harvest = g.getHarvest();
        if (harvest != null) {
            addDrop(out, type, harvest.getItemId(), harvest.getDropListId());
        }
        g.getToolData().values().forEach(t -> addDrop(out, type, t.getItemId(), t.getDropListId()));
    }

    /** {@code itemId}, else the block's own item when it names no list either (BlockHarvestUtils.getDrops). */
    private static void addDrop(Set<String> out, BlockType type, @Nullable String itemId, @Nullable String list) {
        Item own = type.getItem();
        if (itemId != null) {
            out.add(itemId);
        } else if (list == null && own != null) {
            out.add(own.getId());
        }
    }

    /** Every item {@code list} may give, the lists it references included. */
    private static void addListDrops(Set<String> out, @Nullable ItemDropList list) {
        if (list == null || list.getContainer() == null) {
            return;
        }
        for (ItemDrop d : list.getContainer().getAllDrops(new ArrayList<>())) {
            if (d != null && d.getItemId() != null) {
                out.add(d.getItemId());
            }
        }
    }
}
