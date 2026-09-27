package dev.hycolony.plugin.ui.hut;

import dev.hycolony.core.colony.ColonyManager;
import dev.hycolony.core.colony.ui.BuildingView;
import dev.hycolony.core.colony.ui.tab.BuilderTabs;
import dev.hycolony.core.colony.ui.tab.CourierTabs;
import dev.hycolony.core.colony.ui.tab.ModuleTab;
import dev.hycolony.core.colony.ui.tab.WarehouseTabs;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/** Picks the tabs drawing each module tab of a hut's view, in the view's order. */
public final class HutTabs {
    private HutTabs() {}

    /** The tabs of {@code view} after Main, as {@code player} sees them. */
    public static List<HutTab> of(BuildingView view, ColonyManager manager, UUID player) {
        List<HutTab> tabs = new ArrayList<>();
        for (ModuleTab t : view.tabs()) {
            tabs.addAll(
                    switch (t) {
                        case BuilderTabs b ->
                            List.of(
                                    new BuilderResourcesTab(manager, player, view.pos(), b.resources()),
                                    new BuilderSettingsTab(manager, player, view.pos(), b.mode(), view.canManage()),
                                    new BuilderOrdersTab(manager, player, view.pos(), b, view.canManage()));
                        case WarehouseTabs w ->
                            List.of(
                                    new WarehouseCouriersTab(w),
                                    new WarehouseStockTab(w.stock()),
                                    new WarehouseTasksTab(w.queue()));
                        case CourierTabs c -> List.of(new CourierTasksTab(c));
                    });
        }
        return tabs;
    }
}
