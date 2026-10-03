package dev.hycolony.core.construction.blueprint;

import dev.hycolony.core.kernel.item.BlockKey;
import java.util.Optional;

/**
 * The block facts behind Structurize's placement handlers: which world block answers a plan cell, and what a cell
 * costs. Read by the plan matching and the builder's resource costing.
 */
public interface PlacementRules {
    /**
     * Whether a placeholder fill cell may keep this block (Structurize BlockUtils.isGoodFloorBlock): a full solid cube
     * that is not foliage, or tilled soil; false for anything else.
     */
    boolean isGoodFloor(BlockKey block);

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
}
