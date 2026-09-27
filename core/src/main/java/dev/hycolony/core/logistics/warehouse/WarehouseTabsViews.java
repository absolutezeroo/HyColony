package dev.hycolony.core.logistics.warehouse;

import dev.hycolony.core.building.Building;
import dev.hycolony.core.citizen.CitizenData;
import dev.hycolony.core.colony.Colony;
import dev.hycolony.core.colony.ui.tab.WarehouseTabs;
import dev.hycolony.core.kernel.item.ItemAmount;
import java.util.Comparator;
import java.util.List;

/** Builds the warehouse's Couriers, Stock and Tasks tabs. */
final class WarehouseTabsViews {
    private WarehouseTabsViews() {}

    /** The couriers attached through {@code m}, the hut's stock and the tasks waiting for a courier. */
    static WarehouseTabs of(Colony c, Building b, CourierAssignmentModule m) {
        return new WarehouseTabs(
                m.couriers().stream()
                        .flatMap(id -> c.citizens().get(id).stream())
                        .map(CitizenData::name)
                        .toList(),
                CourierAssignmentModule.maxCouriers(b),
                stock(c, b),
                TaskRows.of(
                        c,
                        b.module(WarehouseRequestQueue.class)
                                .map(WarehouseRequestQueue::tokens)
                                .orElse(List.of())));
    }

    /**
     * MC WindowHutAllInventory with its "count, descending" sort: every item of the hut block and racks, most held
     * first, ties by id.
     *
     * <p>Deviation from MC: no sort button nor search field; the list always uses this order.
     */
    private static List<ItemAmount> stock(Colony c, Building b) {
        return c.context().ports().containers().contents(b.containers()).entrySet().stream()
                .filter(e -> e.getValue() > 0)
                .map(e -> new ItemAmount(e.getKey(), e.getValue()))
                .sorted(Comparator.comparingInt(ItemAmount::count)
                        .reversed()
                        .thenComparing(a -> a.item().id()))
                .toList();
    }
}
