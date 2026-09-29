package dev.hycolony.plugin.ui.hut;

import dev.hycolony.core.colony.ColonyManager;
import dev.hycolony.core.colony.ui.BuildingView;
import dev.hycolony.core.colony.ui.tab.BuilderResourcesView;
import dev.hycolony.core.colony.ui.tab.BuilderSettingsView;
import dev.hycolony.core.colony.ui.tab.CourierAssignmentView;
import dev.hycolony.core.colony.ui.tab.CourierTasksView;
import dev.hycolony.core.colony.ui.tab.FieldsView;
import dev.hycolony.core.colony.ui.tab.ModuleTab;
import dev.hycolony.core.colony.ui.tab.RecipesView;
import dev.hycolony.core.colony.ui.tab.WarehouseTasksView;
import dev.hycolony.core.colony.ui.tab.WorkOrderListView;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** Picks the renderer of each module tab of a hut's view (MC module window per module view), in the view's order. */
public final class HutTabs {
    private HutTabs() {}

    /** The tabs of {@code view} after Main, as {@code player} sees them. */
    public static List<HutTab> of(BuildingView view, ColonyManager manager, UUID player) {
        return view.tabs().stream()
                .flatMap(t -> of(t, view, manager, player).stream())
                .toList();
    }

    /** The renderer of {@code tab}; empty for a tab without one yet. */
    private static Optional<HutTab> of(ModuleTab tab, BuildingView view, ColonyManager manager, UUID player) {
        return switch (tab) {
            case BuilderResourcesView r -> Optional.of(new BuilderResourcesTab(manager, player, view.pos(), r));
            case BuilderSettingsView s ->
                Optional.of(new BuilderSettingsTab(manager, player, view.pos(), s, view.canManage()));
            case WorkOrderListView o ->
                Optional.of(new BuilderOrdersTab(manager, player, view.pos(), o, view.canManage()));
            case CourierAssignmentView c -> Optional.of(new WarehouseCouriersTab(c));
            case WarehouseTasksView w -> Optional.of(new WarehouseTasksTab(w.queue()));
            case CourierTasksView c -> Optional.of(new CourierTasksTab(c));
            case FieldsView f -> Optional.of(new FieldsTab(manager, player, view.pos(), f));
            case RecipesView r -> Optional.of(new RecipesTab(manager, player, view.pos(), r, view.canManage()));
        };
    }
}
