package dev.hycolony.plugin.ui.citizen;

import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.server.core.entity.entities.player.pages.CustomUIPage;
import com.hypixel.hytale.server.core.ui.builder.UICommandBuilder;
import com.hypixel.hytale.server.core.ui.builder.UIEventBuilder;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import dev.hycolony.core.colony.ColonyManager;
import dev.hycolony.core.colony.ui.CitizenView;
import dev.hycolony.plugin.IdMap;
import dev.hycolony.plugin.ui.ColonyPage;
import dev.hycolony.plugin.ui.TabBar;
import java.util.ArrayList;
import java.util.List;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;

/**
 * The citizen's window in tabs, in MC AbstractWindowCitizen order: Main, Requests, Inventory, then Job for a citizen
 * with a workplace.
 *
 * <p>Deviation from MC: no Happiness, Family nor Debug tab (no such systems yet), and the Inventory tab lists the
 * items with a button opening the citizen's container (see {@link CitizenInventoryTab}).
 */
public final class CitizenPage extends ColonyPage {
    /** A tab: its content group and its label key. */
    enum Tab implements TabBar.Tab {
        MAIN("#MainTab", "main"),
        REQUESTS("#RequestsTab", "requests"),
        INVENTORY("#InventoryTab", "inventory"),
        JOB("#JobTab", "job");

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
            return "hycolony.ui.citizen.tab." + key;
        }
    }

    private final CitizenView view;
    private final IdMap ids;
    private final CitizenRequestsTab requests;
    private final List<Tab> tabs = new ArrayList<>(List.of(Tab.MAIN, Tab.REQUESTS, Tab.INVENTORY));
    private Tab tab = Tab.MAIN;

    public CitizenPage(PlayerRef playerRef, CitizenView view, ColonyManager manager, IdMap ids) {
        super(playerRef, manager);
        this.view = view;
        this.ids = ids;
        this.requests = new CitizenRequestsTab(manager, player, view);
        if (view.jobSkills().isPresent()) {
            tabs.add(Tab.JOB);
        }
    }

    /** The view drawn, to tell whose window this is. */
    public CitizenView view() {
        return view;
    }

    /** Opens on the tab {@code previous} showed if it is this citizen's window (the core re-shows after actions). */
    public CitizenPage keepTabOf(@Nullable CustomUIPage previous) {
        if (previous instanceof CitizenPage p && p.view.citizenId() == view.citizenId() && tabs.contains(p.tab)) {
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
        ui.append("Pages/HyColony/Citizen.ui");
        TabBar.render(ui, events, tabs, tab);
        CitizenMainTab.render(ui, view, new SkillRowRenderer(ids));
        requests.render(ui, events);
        CitizenInventoryTab.render(ui, events, view.inventory());
        view.jobSkills().ifPresent(j -> CitizenJobTab.render(ui, ids, view.jobId(), j));
    }

    @Override
    public void handleDataEvent(@Nonnull Ref<EntityStore> ref, @Nonnull Store<EntityStore> store, @Nonnull Act act) {
        if ("tab".equals(act.action())) {
            if (act.index() >= 0 && act.index() < tabs.size()) {
                tab = tabs.get(act.index());
                rebuild();
            }
            return;
        }
        if ("openInventory".equals(act.action())) {
            manager.citizenInventories().open(player, view.colonyId(), view.citizenId());
            return;
        }
        requests.handle(act);
    }
}
