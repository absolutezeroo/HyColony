package dev.hycolony.plugin.ui.townhall;

import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.server.core.Message;
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
 * The town hall's window as MC's book (MC AbstractWindowTownHall): the open tab's page appended into {@code #Page},
 * then the bookmarks, each tab at its MC slot.
 *
 * <p>Deviation from MC: no Permissions, Alliances nor Settings tab yet, and a closed tab names itself in a tooltip
 * rather than MC's hover ribbon (Hytale cannot show another element on hover).
 */
public final class TownHallPage extends ColonyPage {
    private static final String PAGE = "#Page";
    private static final String ROOT = PAGE + "[0]";

    /** A tab: its MC bookmark slot ({@code #Mark<slot>}, {@code #Seal<slot>}, {@code #Ribbon<slot>}), page and name. */
    enum Tab {
        ACTIONS(0, "Actions", "actions"),
        INFO(1, "Info", "information"),
        CITIZENS(3, "Citizens", "citizens"),
        STATS(4, "Stats", "stats");

        private final int slot;
        private final String document;
        private final String key;

        Tab(int slot, String document, String key) {
            this.slot = slot;
            this.document = "Pages/HyColony/TownHall/" + document + ".ui";
            this.key = "hycolony.ui.townhall.tab." + key;
        }
    }

    private static final List<Tab> TABS = List.of(Tab.values());

    private final TownHallView view;
    private final TownHallActionsTab actions;
    private final WorkOrderListTab info;
    private final TownHallCitizensTab citizens;
    private final TownHallStatsTab stats;
    private Tab tab = Tab.ACTIONS;

    public TownHallPage(PlayerRef playerRef, TownHallView view, ColonyManager manager) {
        super(playerRef, manager);
        this.view = view;
        this.actions = new TownHallActionsTab(manager, player, view);
        this.info = new WorkOrderListTab(manager, player, view.colonyId(), view.workOrders());
        this.citizens = new TownHallCitizensTab(view.citizens());
        this.stats = new TownHallStatsTab(view.stats());
    }

    /** The open tab's content. */
    private TownHallTab content() {
        return switch (tab) {
            case ACTIONS -> actions;
            case INFO -> info;
            case CITIZENS -> citizens;
            case STATS -> stats;
        };
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

    /** The Home tab holds {@code #RenameInput}, shown only to whoever may rename. */
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
        ui.append(PAGE, tab.document);
        renderBookmarks(ui, events);
        content().render(ui, events, ROOT);
    }

    /**
     * Shows the open tab's long ribbon and the others' short ribbon and wax seal (MC AbstractWindowTownHall
     * constructor). A seal click sends action "tab" with the tab's index.
     */
    private void renderBookmarks(UICommandBuilder ui, UIEventBuilder events) {
        for (int i = 0; i < TABS.size(); i++) {
            Tab t = TABS.get(i);
            boolean open = t == tab;
            ui.set("#Ribbon" + t.slot + ".Visible", open);
            ui.set("#Mark" + t.slot + ".Visible", !open);
            ui.set("#Seal" + t.slot + ".Visible", !open);
            ui.set("#RibbonText" + t.slot + ".Text", Message.translation(t.key));
            ui.set("#Seal" + t.slot + ".TooltipText", Message.translation(t.key));
            if (!open) {
                ColonyPage.bind(events, "#Seal" + t.slot, "tab", i);
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
        content().handle(act);
    }
}
