package dev.hycolony.plugin.ui;

import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.server.core.entity.entities.player.pages.CustomUIPage;
import com.hypixel.hytale.server.core.ui.builder.UICommandBuilder;
import com.hypixel.hytale.server.core.ui.builder.UIEventBuilder;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import dev.hycolony.core.colony.ColonyManager;
import dev.hycolony.core.colony.ui.BuilderTabs;
import dev.hycolony.core.colony.ui.BuildingView;
import dev.hycolony.plugin.ui.logistics.CourierTasksRenderer;
import dev.hycolony.plugin.ui.logistics.WarehouseTabsRenderer;
import java.util.ArrayList;
import java.util.List;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;

/**
 * A hut's window, in tabs as MC AbstractBuildingWindow: Main, then the builder hut's Resources, Settings and Work
 * orders, the warehouse's Couriers, Stock and Tasks, or the courier hut's Tasks (MC module order). Vanilla tab pattern
 * (TriggerVolumeInspectorPage): a {@code #TabButtons} row, the active tab disabled, one content group per tab shown or
 * hidden. The open tab is page state, kept across the core's re-shows.
 */
public final class BuildingPage extends ColonyPage {
    /** A tab: its content group and its label key. */
    enum Tab implements TabBar.Tab {
        MAIN("#MainTab", "main"),
        RESOURCES("#ResourcesTab", "resources"),
        SETTINGS("#SettingsTab", "settings"),
        ORDERS("#OrdersTab", "orders"),
        COURIERS("#CouriersTab", "couriers"),
        STOCK("#StockTab", "stock"),
        WAREHOUSE_TASKS("#WarehouseTasksTab", "tasks"),
        COURIER_TASKS("#CourierTasksTab", "tasks");

        private final String group;
        private final String key;

        Tab(String group, String key) {
            this.group = group;
            this.key = key;
        }

        @Override
        public String group() {
            return group;
        }

        @Override
        public String labelKey() {
            return "hycolony.ui.building.tab." + key;
        }
    }

    private final BuildingView view;
    private final Runnable pickUp;
    private final HutStorage storage;
    private final BuildingMainTab main;
    private final List<Tab> tabs = new ArrayList<>();
    private Tab tab = Tab.MAIN;

    public BuildingPage(PlayerRef playerRef, BuildingView view, ColonyManager manager, Runnable pickUp) {
        super(playerRef, manager);
        this.view = view;
        this.pickUp = pickUp;
        this.storage = new HutStorage(playerRef, manager, view.pos());
        this.main = new BuildingMainTab(manager, player, view);
        tabs.add(Tab.MAIN);
        if (view.builder().isPresent()) {
            tabs.addAll(List.of(Tab.RESOURCES, Tab.SETTINGS, Tab.ORDERS));
        }
        if (view.warehouse().isPresent()) {
            tabs.addAll(List.of(Tab.COURIERS, Tab.STOCK, Tab.WAREHOUSE_TASKS));
        }
        if (view.courier().isPresent()) {
            tabs.add(Tab.COURIER_TASKS);
        }
    }

    /** The view drawn, to tell whose window this is. */
    public BuildingView view() {
        return view;
    }

    /** Opens on the tab {@code previous} showed if it is this hut's window (the core re-shows after each action). */
    public BuildingPage keepTabOf(@Nullable CustomUIPage previous) {
        if (previous instanceof BuildingPage p && p.view.pos().equals(view.pos()) && tabs.contains(p.tab)) {
            tab = p.tab;
        }
        return this;
    }

    @Override
    public void build(
            @Nonnull Ref<EntityStore> ref,
            @Nonnull UICommandBuilder ui,
            @Nonnull UIEventBuilder events,
            @Nonnull Store<EntityStore> store) {
        ui.append("Pages/HyColony/Building.ui");
        TabBar.render(ui, events, tabs, tab);
        main.render(ui, events, storage.mayOpen());
        view.builder().ifPresent(b -> {
            resources(b).render(ui, events);
            settings(b).render(ui, events);
            orders(b).render(ui, events);
        });
        view.warehouse().ifPresent(w -> WarehouseTabsRenderer.render(ui, w));
        view.courier().ifPresent(c -> CourierTasksRenderer.render(ui, c));
    }

    private BuilderResourcesTab resources(BuilderTabs b) {
        return new BuilderResourcesTab(manager, player, view.pos(), b.resources());
    }

    private BuilderSettingsTab settings(BuilderTabs b) {
        return new BuilderSettingsTab(manager, player, view.pos(), b.mode(), view.canManage());
    }

    private BuilderOrdersTab orders(BuilderTabs b) {
        return new BuilderOrdersTab(manager, player, view.pos(), b, view.canManage());
    }

    @Override
    public void handleDataEvent(@Nonnull Ref<EntityStore> ref, @Nonnull Store<EntityStore> store, @Nonnull Act act) {
        switch (act.action) {
            case "tab" -> {
                if (act.index >= 0 && act.index < tabs.size()) {
                    tab = tabs.get(act.index);
                    rebuild();
                }
            }
            case "storage" -> storage.open(ref, store);
            case "pickUp" -> pickUp.run();
            default -> {
                if (main.handle(act)) {
                    rebuild();
                }
                view.builder().ifPresent(b -> {
                    resources(b).handle(act);
                    settings(b).handle(act);
                    orders(b).handle(act);
                });
            }
        }
    }
}
