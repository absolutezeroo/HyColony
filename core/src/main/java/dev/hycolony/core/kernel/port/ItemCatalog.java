package dev.hycolony.core.kernel.port;

import dev.hycolony.core.kernel.item.BlockKey;
import dev.hycolony.core.kernel.item.BlockKind;
import dev.hycolony.core.kernel.item.ItemKey;
import dev.hycolony.core.kernel.item.ToolInfo;
import dev.hycolony.core.kernel.item.ToolType;
import java.util.Optional;

public interface ItemCatalog {
    int maxStack(ItemKey item);

    /** The item to provide to place this block. */
    Optional<ItemKey> itemForBlock(BlockKey block);

    BlockKind kind(BlockKey block);

    boolean isOre(BlockKey block);

    Optional<ToolType> toolFor(BlockKey block);

    float hardness(BlockKey block);

    Optional<ToolInfo> tool(ItemKey item);
}
