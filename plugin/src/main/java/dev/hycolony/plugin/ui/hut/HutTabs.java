package dev.hycolony.plugin.ui.hut;

import com.hypixel.hytale.logger.HytaleLogger;
import dev.hycolony.core.app.ColonyManager;
import dev.hycolony.core.app.ui.BuildingView;
import dev.hycolony.core.building.module.ModuleTab;
import dev.hycolony.core.building.module.SettingsView;
import dev.hycolony.core.citizen.home.ResidentsView;
import dev.hycolony.core.construction.hut.WorkOrderListView;
import dev.hycolony.core.construction.resources.BuilderResourcesView;
import dev.hycolony.core.crafting.furnace.FuelListView;
import dev.hycolony.core.crafting.module.RecipesView;
import dev.hycolony.core.crafting.restaurant.MenuView;
import dev.hycolony.core.crafting.task.CrafterTasksView;
import dev.hycolony.core.farming.hut.FieldsView;
import dev.hycolony.core.logistics.courier.CourierTasksView;
import dev.hycolony.core.logistics.warehouse.CourierAssignmentView;
import dev.hycolony.core.logistics.warehouse.WarehouseTasksView;
import dev.hycolony.plugin.ui.hut.restaurant.FuelTab;
import dev.hycolony.plugin.ui.hut.restaurant.MenuTab;
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
            case CourierAssignmentView c -> Optional.of(new WarehouseCouriersTab(manager, player, view, c));
            case WarehouseTasksView w -> Optional.of(new TasksTab(w.queue(), Optional.empty()));
            case CourierTasksView c -> Optional.of(TasksTab.courier(c));
            case CrafterTasksView c -> Optional.of(new TasksTab(c.tasks(), Optional.empty()));
            case FieldsView f -> Optional.of(new FieldsTab(manager, player, view.pos(), f));
            case RecipesView r -> Optional.of(new RecipesTab(manager, player, view, r));
            case FuelListView f -> Optional.of(new FuelTab(manager, player, view.pos(), f));
            case MenuView m -> Optional.of(new MenuTab(manager, player, view.pos(), m));
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
