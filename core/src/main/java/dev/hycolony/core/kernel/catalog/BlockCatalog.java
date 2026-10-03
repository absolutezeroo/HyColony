package dev.hycolony.core.kernel.catalog;

import dev.hycolony.core.kernel.item.BlockItems;
import dev.hycolony.core.kernel.item.BlockKey;
import dev.hycolony.core.kernel.item.BlockKind;
import dev.hycolony.core.kernel.item.ToolType;
import java.util.Optional;

/** What the game says of a block type: its kind, how it is mined, the items placing it, and what bodies do on it. */
public interface BlockCatalog {
    /** The items of this block, from which the builder picks the one placing it; {@link BlockItems#NONE} if none. */
    BlockItems blockItems(BlockKey block);

    /** The block's kind (air, solid, non-solid, fluid or unbreakable); UNBREAKABLE for an unknown block. */
    BlockKind kind(BlockKey block);

    /** Whether this block is an ore, whose drops the builder does not keep (MC EntityAIStructureBuilder.mineBlock). */
    boolean isOre(BlockKey block);

    /** The tool type that mines this block; empty when no tool is needed (MC AbstractEntityAIBasic NO_TOOL). */
    Optional<ToolType> toolFor(BlockKey block);

    /** MC block hardness, which sets the break delay; 1 for an unknown block, 0 for air and fluids. */
    float hardness(BlockKey block);

    /**
     * Whether a body in or on this block is hurt: a harmful fluid (lava, fire) or a block that damages or burns on
     * contact (campfire, brazier, cactus). MC PathfindingUtils.isDangerous; false for anything else.
     */
    boolean isHarmful(BlockKey block);

    /** Whether this block is a bed citizens lie in: its block type has sleeping points (Hytale BlockType.getBeds). */
    boolean isBed(BlockKey block);

    /** Whether citizens sit on this block: its block type has seats (Hytale BlockType.getSeats). */
    boolean isSeat(BlockKey block);
}
