package dev.hycolony.core.crafting.furnace;

import dev.hycolony.core.kernel.BlockPos;
import dev.hycolony.core.kernel.item.ItemAmount;
import java.util.List;
import java.util.Optional;

/**
 * The cooking stations of the world, by position (MC AbstractFurnaceBlockEntity). Never throws: a position that is not
 * a loaded station has no contents, takes nothing and gives nothing.
 */
public interface CookingStations {
    /** What the station at {@code pos} holds; empty when no loaded station is there. */
    Optional<StationContents> contents(BlockPos pos);

    /** Puts what fits of {@code stack} in the station's input; returns how many went in. */
    int insertInput(BlockPos pos, ItemAmount stack);

    /** Puts what fits of {@code stack} in the station's fuel slot; returns how many went in. */
    int insertFuel(BlockPos pos, ItemAmount stack);

    /** Takes everything out of the station's output (MC RESULT_SLOT). */
    List<ItemAmount> extractOutput(BlockPos pos);

    /** Takes everything out of the station's fuel slot (MC FUEL_SLOT). */
    List<ItemAmount> extractFuel(BlockPos pos);

    /**
     * Lights the station when it holds input and fuel (a MC furnace lights itself; Hytale's bench must be turned on);
     * true once it burns.
     */
    boolean light(BlockPos pos);

    /** Makes a burning station work {@code ticks} game ticks more at once (MC accelerateFurnaces' serverTick calls). */
    void accelerate(BlockPos pos, int ticks);
}
