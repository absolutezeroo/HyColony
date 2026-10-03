package dev.hycolony.core.kernel.port;

import dev.hycolony.core.kernel.item.BlockItems;
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

    /** The items of this block, from which the builder picks the one placing it; {@link BlockItems#NONE} if none. */
    BlockItems blockItems(BlockKey block);

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

    /** Whether this block is of MC's dirt tag (dirt, grass, podzol, coarse dirt, moss, mud): false for any other. */
    boolean isDirt(BlockKey block);

    /** Whether a plan cell of this block is done by any {@link #isDirt} block: grass, dirt (GrassPlacementHandler). */
    boolean takesAnyDirt(BlockKey block);

    /** Whether this block is a dirt path: it costs dirt, none on dirt (Structurize BlockGrassPathPlacementHandler). */
    boolean isDirtPath(BlockKey block);

    /** MC's {@code Blocks.DIRT}: the block whose item a grass, dirt or path cell costs; empty if the map has none. */
    Optional<BlockKey> plainDirt();

    /** Whether this block is a leaf, which the builder places for free (MC BuildingStructureHandler.isStackFree). */
    boolean isLeaves(BlockKey block);

    /** Whether this block is a fluid source, not a flowing fluid nor any other block. */
    boolean isFluidSource(BlockKey block);

    /**
     * The family of a wall, fence, bars or gate whose shape follows its neighbours (Structurize
     * GeneralBlockPlacementHandler): any shape of it answers a cell of another; empty for any other block.
     */
    Optional<String> shapeFamily(BlockKey block);

    /** What eating {@code item} gives (MC ItemStackUtils.ISFOOD and FoodProperties); empty for no food. */
    Optional<FoodInfo> food(ItemKey item);

    /** Every item {@link #food} knows, in a set order (MC CompatibilityManager's edibles, before filtering). */
    List<ItemKey> foods();

    /** Every item {@link #tool} knows, in no set order (the tools MC's ToolRequest shows, sorted by it). */
    List<ItemKey> tools();

    /**
     * What cooking {@code item} gives (MC the furnace's smelting result; in Hytale the campfire's); empty when it
     * does not cook.
     */
    Optional<ItemKey> cooked(ItemKey item);

    Optional<ToolType> toolFor(BlockKey block);

    float hardness(BlockKey block);

    Optional<ToolInfo> tool(ItemKey item);

    /**
     * How many blocks the tool mines before it breaks (its uses, MC max damage), or how many hits an armour piece
     * takes; 0 = unbreakable (or neither).
     */
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
