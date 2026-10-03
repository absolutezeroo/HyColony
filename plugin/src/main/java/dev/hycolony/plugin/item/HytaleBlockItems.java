package dev.hycolony.plugin.item;

import com.hypixel.hytale.logger.HytaleLogger;
import com.hypixel.hytale.server.core.asset.type.blocktype.config.BlockBreakingDropType;
import com.hypixel.hytale.server.core.asset.type.blocktype.config.BlockGathering;
import com.hypixel.hytale.server.core.asset.type.blocktype.config.BlockPlacementSettings;
import com.hypixel.hytale.server.core.asset.type.blocktype.config.BlockType;
import com.hypixel.hytale.server.core.asset.type.blocktype.config.SoftBlockDropType;
import com.hypixel.hytale.server.core.asset.type.item.config.Item;
import com.hypixel.hytale.server.core.asset.type.item.config.ItemDrop;
import com.hypixel.hytale.server.core.asset.type.item.config.ItemDropList;
import com.hypixel.hytale.server.core.asset.type.item.config.container.SingleItemDropContainer;
import dev.hycolony.core.kernel.item.BlockItems;
import dev.hycolony.core.kernel.item.ItemAmount;
import dev.hycolony.core.kernel.item.ItemKey;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;
import java.util.logging.Level;
import org.jspecify.annotations.Nullable;

/**
 * The items of a Hytale block type, read from the assets: its own (container) item, whether a survival player can get
 * it ({@link HytaleItemSources}), the item whose placement override makes the block ({@code Wall}, {@code Floor} or
 * {@code CeilingPlacementOverrideBlockId}: Furniture_Crude_Torch → Wood_Torch_Wall), and the single fixed stack
 * breaking it gives (BlockHarvestUtils.getDrops: {@code ItemId} × {@code Quantity}, or a drop list holding one
 * {@link SingleItemDropContainer} of fixed quantity). The placement index is read once, at the first call. World
 * thread only.
 */
public final class HytaleBlockItems {
    private static final HytaleLogger LOG = HytaleLogger.forEnclosingClass();

    private final HytaleItemSources sources = new HytaleItemSources();
    private @Nullable Map<String, String> placedBy;
    private boolean warned;

    /**
     * The items of {@code type}; {@link BlockItems#NONE} when it has no item, its own item alone when the rest of its
     * assets cannot be read (the first failure logged, the next ones at FINE).
     */
    public BlockItems of(BlockType type) {
        Item item = type.getItem();
        if (item == null) {
            return BlockItems.NONE;
        }
        try {
            return new BlockItems(
                    Optional.of(new ItemKey(item.getId())),
                    sources.hasSource(item.getId()),
                    Optional.ofNullable(placedBy().get(type.getId())).map(ItemKey::new),
                    breakDrop(type));
        } catch (RuntimeException e) {
            LOG.at(warned ? Level.FINE : Level.WARNING).withCause(e).log(
                    "Items of block %s unreadable: its own item is asked", type.getId());
            warned = true;
            return BlockItems.of(new ItemKey(item.getId()));
        }
    }

    /** The single fixed stack breaking {@code type} gives ({@code Soft} without {@code Breaking}); else empty. */
    private static Optional<ItemAmount> breakDrop(BlockType type) {
        BlockGathering g = type.getGathering();
        if (g == null) {
            return Optional.empty();
        }
        BlockBreakingDropType breaking = g.getBreaking();
        SoftBlockDropType soft = g.getSoft();
        if (breaking != null) {
            return drop(breaking.getItemId(), breaking.getDropListId(), breaking.getQuantity());
        }
        return soft == null ? Optional.empty() : drop(soft.getItemId(), soft.getDropListId(), 1);
    }

    /** {@code quantity} of the item, or of a fixed single drop list; empty for both, neither or a random list. */
    private static Optional<ItemAmount> drop(@Nullable String itemId, @Nullable String dropList, int quantity) {
        if (quantity <= 0) {
            return Optional.empty();
        }
        if (itemId != null && dropList == null) {
            return Optional.of(new ItemAmount(new ItemKey(itemId), quantity));
        }
        if (itemId == null && dropList != null) {
            return singleDrop(dropList)
                    .map(d -> new ItemAmount(new ItemKey(d.getItemId()), d.getQuantityMin() * quantity));
        }
        return Optional.empty();
    }

    /** The drop of a list that always gives one item in a fixed quantity (a top-level single container); else empty. */
    private static Optional<ItemDrop> singleDrop(String dropListId) {
        ItemDropList list = ItemDropList.getAssetMap().getAsset(dropListId);
        if (list == null || !(list.getContainer() instanceof SingleItemDropContainer single)) {
            return Optional.empty();
        }
        ItemDrop d = single.getDrop();
        boolean fixed = d.getItemId() != null && d.getQuantityMin() > 0 && d.getQuantityMin() == d.getQuantityMax();
        return fixed ? Optional.of(d) : Optional.empty();
    }

    /** Block id → the item whose placement override makes it; the first item by id wins when several do. */
    private Map<String, String> placedBy() {
        if (placedBy == null) {
            Map<String, String> out = new HashMap<>();
            for (Item item : new TreeMap<>(Item.getAssetMap().getAssetMap()).values()) {
                BlockType type = item == null || item.getBlockId() == null
                        ? null
                        : BlockType.getAssetMap().getAsset(item.getBlockId());
                BlockPlacementSettings p = type == null ? null : type.getPlacementSettings();
                if (p != null) {
                    putOverride(out, p.getWallPlacementOverrideBlockId(), item.getId());
                    putOverride(out, p.getFloorPlacementOverrideBlockId(), item.getId());
                    putOverride(out, p.getCeilingPlacementOverrideBlockId(), item.getId());
                }
            }
            placedBy = out;
        }
        return placedBy;
    }

    private static void putOverride(Map<String, String> out, @Nullable String block, String item) {
        if (block != null) {
            out.putIfAbsent(block, item);
        }
    }
}
