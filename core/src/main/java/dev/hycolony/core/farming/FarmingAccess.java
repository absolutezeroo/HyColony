package dev.hycolony.core.farming;

import dev.hycolony.core.kernel.BlockPos;
import dev.hycolony.core.kernel.item.ItemAmount;
import dev.hycolony.core.kernel.item.ItemKey;
import java.util.List;

/**
 * Port: Hytale's soil and crops, what the farmer reads and changes in the world (sp3b-hytale-farming § 2-4). It lives
 * here rather than in {@code kernel/port}, like {@code RecipeCatalog}. Never throws: an unknown or unloaded position
 * answers false, {@link CropState#NONE} or empty.
 */
public interface FarmingAccess {
    /** Whether a hoe turns this block into tilled soil (the 16 soils of Hytale's Hoe_Till). */
    boolean isTillable(BlockPos pos);

    /** Whether this block is tilled soil, whatever its watered or fertilized state. */
    boolean isTilled(BlockPos pos);

    /** Turns the block into tilled soil that decays and grows crops like a player's; false if it cannot. */
    boolean till(BlockPos pos);

    /** Whether the tilled soil at {@code pos} is fertilized. */
    boolean isFertilized(BlockPos pos);

    /** Fertilizes the tilled soil at {@code pos} for good (Hytale FertilizeSoil); false if it is no tilled soil. */
    boolean fertilize(BlockPos pos);

    /** The crop at {@code pos} (a crop stands on its soil, one block up). */
    CropState crop(BlockPos pos);

    /** Places the crop {@code seed} grows into at {@code pos}, growing from its first stage; false if not possible. */
    boolean plant(BlockPos pos, ItemKey seed);

    /**
     * Harvests the mature crop at {@code pos} and returns its harvest drops: a normal crop leaves the block empty, an
     * eternal one goes back to its first stage. Empty if nothing mature is there.
     */
    List<ItemAmount> harvest(BlockPos pos);

    /** Whether the block is a fence, gate or wall, which keeps the cell below out of a field (MC isNoPartOfField). */
    boolean isFieldBarrier(BlockPos pos);

    /** Every crop seed a field may be set to, sorted by id. */
    List<ItemKey> seeds();

    /** Whether the field block stands at {@code pos}. */
    boolean isFieldBlock(BlockPos pos);

    /** The fertilizer tool item, named by the id-map. */
    ItemKey fertilizerItem();
}
