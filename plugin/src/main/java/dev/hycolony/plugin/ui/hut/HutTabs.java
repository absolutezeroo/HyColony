package dev.hycolony.plugin.ui.hut;

import dev.hycolony.core.colony.ColonyManager;
import dev.hycolony.core.colony.ui.BuildingView;
import dev.hycolony.core.colony.ui.tab.BuilderResourcesView;
import dev.hycolony.core.colony.ui.tab.BuilderSettingsView;
import dev.hycolony.core.colony.ui.tab.CourierAssignmentView;
import dev.hycolony.core.colony.ui.tab.CourierTasksView;
import dev.hycolony.core.colony.ui.tab.ModuleTab;
import dev.hycolony.core.colony.ui.tab.WarehouseTasksView;
import dev.hycolony.core.colony.ui.tab.WorkOrderListView;
import java.util.List;
import java.util.UUID;

/** Picks the renderer of each module tab of a hut's view (MC module window per module view), in the view's order. */
public final class HutTabs {
    private HutTabs() {}

    /** The tabs of {@code view} after Main, as {@code player} sees them. */
    public static List<HutTab> of(BuildingView view, ColonyManager manager, UUID player) {
        return view.tabs().stream().map(t -> of(t, view, manager, player)).toList();
    }

    private static HutTab of(ModuleTab tab, BuildingView view, ColonyManager manager, UUID player) {
        return switch (tab) {
            case BuilderResourcesView r -> new BuilderResourcesTab(manager, player, view.pos(), r);
            case BuilderSettingsView s ->
                new BuilderSettingsTab(manager, player, view.pos(), s.mode(), view.canManage());
            case WorkOrderListView o -> new BuilderOrdersTab(manager, player, view.pos(), o, view.canManage());
            case CourierAssignmentView c -> new WarehouseCouriersTab(c);
            case WarehouseTasksView w -> new WarehouseTasksTab(w.queue());
            case CourierTasksView c -> new CourierTasksTab(c);
        };
    }
}
