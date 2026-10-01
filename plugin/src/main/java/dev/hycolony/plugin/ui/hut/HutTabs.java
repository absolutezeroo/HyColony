package dev.hycolony.plugin.ui.hut;

import com.hypixel.hytale.logger.HytaleLogger;
import dev.hycolony.core.app.ColonyManager;
import dev.hycolony.core.app.ui.BuildingView;
import dev.hycolony.core.building.module.ModuleTab;
import dev.hycolony.core.building.module.SettingsView;
import dev.hycolony.core.citizen.home.ResidentsView;
import dev.hycolony.core.construction.hut.WorkOrderListView;
import dev.hycolony.core.construction.resources.BuilderResourcesView;
import dev.hycolony.core.crafting.module.RecipesView;
import dev.hycolony.core.farming.hut.FieldsView;
import dev.hycolony.core.logistics.courier.CourierTasksView;
import dev.hycolony.core.logistics.warehouse.CourierAssignmentView;
import dev.hycolony.core.logistics.warehouse.WarehouseTasksView;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.logging.Level;

/** Picks the renderer of each module tab of a hut's view (MC module window per module view), in the view's order. */
public final class HutTabs {
    private static final HytaleLogger LOG = HytaleLogger.forEnclosingClass();

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
            case SettingsView s -> Optional.of(new SettingsTab(manager, player, view.pos(), s));
            case WorkOrderListView o -> Optional.of(new BuilderOrdersTab(manager, player, view.pos(), o));
            case CourierAssignmentView c -> Optional.of(new WarehouseCouriersTab(c));
            case WarehouseTasksView w -> Optional.of(new WarehouseTasksTab(w.queue()));
            case CourierTasksView c -> Optional.of(new CourierTasksTab(c));
            case FieldsView f -> Optional.of(new FieldsTab(manager, player, view.pos(), f));
            case RecipesView r -> Optional.of(new RecipesTab(manager, player, view.pos(), r, view.canManage()));
            // MC LivingBuildingModuleView has no page: the residence's main page and assign window show it.
            case ResidentsView _ -> Optional.empty();
            default -> {
                LOG.at(Level.FINE).log(
                        "No renderer for the hut tab %s", tab.getClass().getSimpleName());
                yield Optional.empty();
            }
        };
    }
}
