package dev.hycolony.plugin.prefab;

import com.hypixel.hytale.server.core.asset.type.blocktype.config.BlockType;
import com.hypixel.hytale.server.core.asset.type.item.config.Item;
import dev.hycolony.core.kernel.item.BlockKey;
import dev.hycolony.core.kernel.item.BlockKind;
import dev.hycolony.core.kernel.port.ItemCatalog;
import java.util.List;
import org.jspecify.annotations.Nullable;

/**
 * The blocks a builder hut may fill placeholder cells with (MC BlockSetting's list of block items): every base block
 * (no state variant) whose item has its own id, that the builder may mine and place, and that is a good floor, sorted
 * by id. Read once from the loaded assets, on the first call (world thread).
 */
final class FillBlocks {
    private final ItemCatalog catalog;
    private @Nullable List<BlockKey> choices;

    FillBlocks(ItemCatalog catalog) {
        this.catalog = catalog;
    }

    /** The choices; empty when no asset qualifies. */
    List<BlockKey> choices() {
        if (choices == null) {
            choices = BlockType.getAssetMap().getAssetMap().entrySet().stream()
                    .filter(e -> qualifies(e.getKey(), e.getValue()))
                    .map(e -> new BlockKey(e.getKey()))
                    .sorted((a, b) -> a.id().compareTo(b.id()))
                    .toList();
        }
        return choices;
    }

    /** A base block placed by its own item, breakable, and a good floor (see ItemCatalog.isGoodFloor). */
    private boolean qualifies(String id, BlockType type) {
        Item item = type.getItem();
        BlockKey key = new BlockKey(id);
        return !id.startsWith("*")
                && item != null
                && id.equals(item.getId())
                && catalog.kind(key) == BlockKind.SOLID
                && catalog.isGoodFloor(key);
    }
}
