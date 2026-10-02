package dev.hycolony.core.kernel.port;

import dev.hycolony.core.kernel.item.BlockKey;
import dev.hycolony.core.kernel.item.BlockKind;
import dev.hycolony.core.kernel.item.FoodInfo;
import dev.hycolony.core.kernel.item.ItemAmount;
import dev.hycolony.core.kernel.item.ItemKey;
import dev.hycolony.core.kernel.item.ToolInfo;
import dev.hycolony.core.kernel.item.ToolType;
import java.util.List;
import java.util.Optional;

public interface ItemCatalog {
    int maxStack(ItemKey item);

    /** The item to provide to place this block. */
    Optional<ItemKey> itemForBlock(BlockKey block);

    BlockKind kind(BlockKey block);

    boolean isOre(BlockKey block);

    /**
     * Whether a placeholder fill cell may keep this block (Structurize BlockUtils.isGoodFloorBlock): a full solid cube
     * that is not foliage, or tilled soil; false for anything else.
     */
    boolean isGoodFloor(BlockKey block);

    /**
     * Whether a body in or on this block is hurt: a harmful fluid (lava, fire) or a block that damages or burns on
     * contact (campfire, brazier, cactus). MC PathfindingUtils.isDangerous; false for anything else.
     */
    boolean isHarmful(BlockKey block);

    /** Whether this block is a bed citizens lie in: its block type has sleeping points (Hytale BlockType.getBeds). */
    boolean isBed(BlockKey block);

    /** Whether citizens sit on this block: its block type has seats (Hytale BlockType.getSeats). */
    boolean isSeat(BlockKey block);

    /** What eating {@code item} gives (MC ItemStackUtils.ISFOOD and FoodProperties); empty for no food. */
    Optional<FoodInfo> food(ItemKey item);

    /** Every item {@link #food} knows, in a set order (MC CompatibilityManager's edibles, before filtering). */
    List<ItemKey> foods();

    /** Every item {@link #tool} knows, by id (the tools MC's ToolRequest shows). */
    List<ItemKey> tools();

    /**
     * What cooking {@code item} gives (MC the furnace's smelting result; in Hytale the campfire's); empty when it
     * does not cook.
     */
    Optional<ItemKey> cooked(ItemKey item);

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
