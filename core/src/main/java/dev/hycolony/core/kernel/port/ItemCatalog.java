package dev.hycolony.core.kernel.port;

import dev.hycolony.core.kernel.item.BlockKey;
import dev.hycolony.core.kernel.item.BlockKind;
import dev.hycolony.core.kernel.item.ItemAmount;
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

    /**
     * Whether a body in or on this block is hurt: a harmful fluid (lava, fire) or a block that damages or burns on
     * contact (campfire, brazier, cactus). MC PathfindingUtils.isDangerous; false for anything else.
     */
    boolean isHarmful(BlockKey block);

    Optional<ToolType> toolFor(BlockKey block);

    float hardness(BlockKey block);

    Optional<ToolInfo> tool(ItemKey item);

    /** How many blocks the tool mines before it breaks (its uses, MC max damage); 0 = unbreakable (or not a tool). */
    int durability(ItemKey item);

    /**
     * Whether {@code stack} is worn to its {@link #durability}: a tool Hytale broke (it keeps it at 0 durability, MC
     * destroys it). Such a stack no longer exists for MC, so it never answers a request nor serves as a tool.
     */
    default boolean wornOut(ItemAmount stack) {
        int uses = durability(stack.item());
        return uses > 0 && stack.damage() >= uses;
    }
}
