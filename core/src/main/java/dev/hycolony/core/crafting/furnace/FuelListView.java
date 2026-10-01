package dev.hycolony.core.crafting.furnace;

import dev.hycolony.core.building.module.ModuleTab;
import dev.hycolony.core.kernel.item.ItemKey;
import java.util.List;

/** A hut's Fuel tab (MC ItemListModuleWindow for FUEL_LIST): every fuel, and whether the worker may burn it. */
public record FuelListView(List<Row> rows) implements ModuleTab {
    /** Keeps its own copy of the rows. */
    public FuelListView {
        rows = List.copyOf(rows);
    }

    /** One fuel and whether it is allowed. */
    public record Row(ItemKey item, boolean allowed) {}
}
