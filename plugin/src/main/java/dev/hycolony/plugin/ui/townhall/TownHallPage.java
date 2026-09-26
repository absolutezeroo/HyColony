package dev.hycolony.plugin.ui.townhall;

import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.server.core.entity.entities.player.pages.CustomUIPage;
import com.hypixel.hytale.server.core.ui.builder.UICommandBuilder;
import com.hypixel.hytale.server.core.ui.builder.UIEventBuilder;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import dev.hycolony.core.colony.ColonyManager;
import dev.hycolony.core.colony.ui.TownHallView;
import dev.hycolony.plugin.ui.ColonyPage;
import dev.hycolony.plugin.ui.TabBar;
import java.util.List;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;

/**
 * The town hall's window in tabs, in MC AbstractWindowTownHall order: Actions, Information, Citizens, Statistics.
 *
 * <p>Deviation from MC: no Permissions, Alliances nor Settings tab (no window for those systems yet).
 */
public final class TownHallPage extends ColonyPage {
    /** A tab: its content group and its label key. */
    enum Tab implements TabBar.Tab {
        ACTIONS("#ActionsTab", "actions"),
        INFO("#InfoTab", "information"),
        CITIZENS("#CitizensTab", "citizens"),
        STATS("#StatsTab", "stats");

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
            return "hycolony.ui.townhall.tab." + key;
        }
    }

    private static final List<Tab> TABS = List.of(Tab.values());

    private final TownHallView view;
    private final TownHallActionsTab actions;
    private final WorkOrderListTab orders;
    private Tab tab = Tab.ACTIONS;

    public TownHallPage(PlayerRef playerRef, TownHallView view, ColonyManager manager) {
        super(playerRef, manager);
        this.view = view;
        this.actions = new TownHallActionsTab(manager, player, view);
        this.orders = new WorkOrderListTab(manager, player, view.colonyId(), view.workOrders());
    }

    /** Opens on the tab {@code previous} showed if it is this colony's town hall (the core re-shows after actions). */
    public TownHallPage keepTabOf(@Nullable CustomUIPage previous) {
        if (previous instanceof TownHallPage p && p.view.colonyId() == view.colonyId()) {
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
        ui.append("Pages/HyColony/TownHall.ui");
        TabBar.render(ui, events, TABS, tab);
        actions.render(ui, events);
        orders.render(ui, events);
        TownHallCitizensTab.render(ui, view.citizens());
        TownHallStatsTab.render(ui, view.stats());
    }

    @Override
    public void handleDataEvent(@Nonnull Ref<EntityStore> ref, @Nonnull Store<EntityStore> store, @Nonnull Act act) {
        if ("tab".equals(act.action())) {
            if (act.index() >= 0 && act.index() < TABS.size()) {
                tab = TABS.get(act.index());
                rebuild();
            }
            return;
        }
        actions.handle(act);
        orders.handle(act);
    }
}
