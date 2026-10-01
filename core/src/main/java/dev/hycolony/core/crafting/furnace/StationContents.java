package dev.hycolony.core.crafting.furnace;

import dev.hycolony.core.kernel.item.ItemAmount;
import java.util.List;

/**
 * What a cooking station holds now (MC AbstractFurnaceBlockEntity's three slots; Hytale's campfire has two input
 * slots, one fuel slot and four output slots) and whether it burns.
 */
public record StationContents(List<ItemAmount> input, List<ItemAmount> fuel, List<ItemAmount> output, boolean lit) {
    /** Keeps its own copies of the slots. */
    public StationContents {
        input = List.copyOf(input);
        fuel = List.copyOf(fuel);
        output = List.copyOf(output);
    }

    public int inputCount() {
        return count(input);
    }

    public int fuelCount() {
        return count(fuel);
    }

    public int outputCount() {
        return count(output);
    }

    private static int count(List<ItemAmount> slots) {
        return slots.stream().mapToInt(ItemAmount::count).sum();
    }
}
