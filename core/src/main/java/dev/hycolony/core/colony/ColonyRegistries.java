package dev.hycolony.core.colony;

import dev.hycolony.core.colony.stats.ColonyStatistics;
import dev.hycolony.core.crafting.recipe.RecipeRegistry;
import dev.hycolony.core.farming.field.FieldRegistry;
import dev.hycolony.core.kernel.BlockPos;
import java.util.function.Predicate;

/**
 * The colony-wide registries its features keep (MC IColonyManager.getRecipeManager, per colony here, and
 * RegisteredStructureManager's building extensions): the recipes its huts learnt, its fields and its statistics.
 */
public final class ColonyRegistries {
    private final RecipeRegistry recipes = new RecipeRegistry();
    private final FieldRegistry fields = new FieldRegistry();
    private final ColonyStatistics statistics = new ColonyStatistics();
    private final Predicate<BlockPos> isFieldBlock;

    /** @param isFieldBlock whether the world holds a field block at a position (the farming port) */
    ColonyRegistries(Predicate<BlockPos> isFieldBlock) {
        this.isFieldBlock = isFieldBlock;
    }

    /** The recipes the colony's huts learnt or improved. */
    public RecipeRegistry recipes() {
        return recipes;
    }

    /** The colony's fields. */
    public FieldRegistry fields() {
        return fields;
    }

    /** The colony's statistics (MC IColony.getStatisticsManager). */
    public ColonyStatistics statistics() {
        return statistics;
    }

    /**
     * A plan just placed a block at {@code pos}: a field block becomes one of the colony's fields (MC
     * FieldPlacementHandler.handle -> BlockScarecrow.setPlacedBy). True when a field was added, for the caller to mark
     * the colony dirty. A field block found already in place is left to its first use, as in MC.
     */
    public boolean planBlockPlaced(BlockPos pos) {
        return isFieldBlock.test(pos) && fields.add(pos);
    }
}
