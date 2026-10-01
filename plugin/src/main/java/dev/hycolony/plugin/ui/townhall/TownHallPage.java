package dev.hycolony.plugin.ui.townhall;

import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.server.core.entity.entities.player.pages.CustomUIPage;
import com.hypixel.hytale.server.core.ui.builder.UICommandBuilder;
import com.hypixel.hytale.server.core.ui.builder.UIEventBuilder;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import dev.hycolony.core.app.ColonyManager;
import dev.hycolony.core.app.ui.TownHallView;
import dev.hycolony.plugin.ui.ColonyPage;
import java.util.List;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;

/**
 * The town hall's window as MC's book, in MC AbstractWindowTownHall tab order: Actions, Information, Citizens,
 * Statistics.
 *
 * <p>Deviation from MC: no Permissions, Alliances nor Settings tab (no window for those systems yet), and a closed
 * tab names itself in a tooltip rather than MC's hover ribbon.
 */
public final class TownHallPage extends ColonyPage {
    /** A tab, in the order of its bookmark ({@code #Mark<i>}, {@code #Seal<i>}, {@code #Ribbon<i>}): its group. */
    enum Tab {
        ACTIONS("#ActionsTab"),
        INFO("#InfoTab"),
        CITIZENS("#CitizensTab"),
        STATS("#StatsTab");

        private final String group;

        Tab(String group) {
            this.group = group;
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

    /** The view drawn, to tell whose window this is. */
    public TownHallView view() {
        return view;
    }

    /** Opens on the tab {@code previous} showed if it is this colony's town hall (the core re-shows after actions). */
    public TownHallPage keepTabOf(@Nullable CustomUIPage previous) {
        if (previous instanceof TownHallPage p && p.view.colonyId() == view.colonyId()) {
            tab = p.tab;
        }
        return this;
    }

    /** The Actions tab holds {@code #RenameInput}, shown only to whoever may rename. */
    @Override
    protected boolean showsInput() {
        return tab == Tab.ACTIONS && view.canRename();
    }

    @Override
    public void build(
            @Nonnull Ref<EntityStore> ref,
            @Nonnull UICommandBuilder ui,
            @Nonnull UIEventBuilder events,
            @Nonnull Store<EntityStore> store) {
        ui.append("Pages/HyColony/TownHall.ui");
        renderTabs(ui, events);
        actions.render(ui, events);
        orders.render(ui, events);
        TownHallCitizensTab.render(ui, view.citizens());
        TownHallStatsTab.render(ui, view.stats());
    }

    /**
     * Shows the open tab's long ribbon and content, and the others' short ribbon and wax seal (MC
     * AbstractWindowTownHall constructor). A seal click sends action "tab" with its index.
     */
    private void renderTabs(UICommandBuilder ui, UIEventBuilder events) {
        for (int i = 0; i < TABS.size(); i++) {
            boolean open = TABS.get(i) == tab;
            ui.set("#Ribbon" + i + ".Visible", open);
            ui.set("#Mark" + i + ".Visible", !open);
            ui.set("#Seal" + i + ".Visible", !open);
            ui.set(TABS.get(i).group + ".Visible", open);
            if (!open) {
                ColonyPage.bind(events, "#Seal" + i, "tab", i);
            }
        }
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
