package dev.hycolony.plugin.ui.citizen;

import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.server.core.entity.entities.player.pages.CustomUIPage;
import com.hypixel.hytale.server.core.ui.builder.UICommandBuilder;
import com.hypixel.hytale.server.core.ui.builder.UIEventBuilder;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import dev.hycolony.core.app.ColonyManager;
import dev.hycolony.core.app.ui.CitizenView;
import dev.hycolony.plugin.IdMap;
import dev.hycolony.plugin.ui.ColonyPage;
import dev.hycolony.plugin.ui.request.RequestDetailPage;
import java.util.ArrayList;
import java.util.List;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;

/**
 * The citizen's window as MC's (AbstractWindowCitizen and its pages): the colonist paper, the side tabs of nav.xml in
 * MC's order, Main, Requests, Inventory, Happiness, then Job for a citizen with a workplace, and the open tab's page.
 * Moving from tab to tab stays in this window (MC opens a window per tab); no tab looks open, as in MC. The Inventory
 * tab opens the citizen's container as the game's container screen, as MC's OpenInventoryMessage opens its container
 * screen.
 *
 * <p>Deviation from MC: no Family nor Debug tab (no such systems yet).
 */
public final class CitizenPage extends ColonyPage {
    /** A tab: its page group and the button that opens it (Citizen.ui); Inventory has no page of its own. */
    enum Tab {
        MAIN("#MainPage", "#MainHit"),
        REQUESTS("#RequestsPage", "#RequestsHit"),
        INVENTORY("", "#InventoryHit"),
        HAPPINESS("#HappinessPage", "#HappinessHit"),
        JOB("#JobPage", "#JobHit");

        private final String page;
        private final String hit;

        Tab(String page, String hit) {
            this.page = page;
            this.hit = hit;
        }
    }

    private final CitizenView view;
    private final CitizenRequestsTab requests;
    private final CitizenSkillLines skills;
    private final List<Tab> tabs = new ArrayList<>(List.of(Tab.MAIN, Tab.REQUESTS, Tab.INVENTORY, Tab.HAPPINESS));
    private Tab tab = Tab.MAIN;

    public CitizenPage(PlayerRef playerRef, CitizenView view, ColonyManager manager, IdMap ids) {
        super(playerRef, manager);
        this.view = view;
        this.requests = new CitizenRequestsTab(manager, playerRef, view, ids);
        this.skills = new CitizenSkillLines(manager, player, view);
        if (view.jobSkills().isPresent()) {
            tabs.add(Tab.JOB);
        }
    }

    /** The view drawn, to tell whose window this is. */
    public CitizenView view() {
        return view;
    }

    /**
     * Opens on the tab {@code previous} showed if it is this citizen's window (the core re-shows after actions), with
     * the skill it had hovered. A request's details opened from it count as it (their Back shows it again).
     */
    public CitizenPage keepTabOf(@Nullable CustomUIPage previous) {
        CustomUIPage shown = previous instanceof RequestDetailPage d ? d.origin() : previous;
        if (shown instanceof CitizenPage p && shows(p.view.colonyId(), p.view.citizenId()) && tabs.contains(p.tab)) {
            tab = p.tab;
            skills.keepHoverOf(p.skills);
        }
        return this;
    }

    /** Whether this is the window of that citizen. */
    boolean shows(int colonyId, int citizenId) {
        return view.colonyId() == colonyId && view.citizenId() == citizenId;
    }

    @Override
    public void build(
            @Nonnull Ref<EntityStore> ref,
            @Nonnull UICommandBuilder ui,
            @Nonnull UIEventBuilder events,
            @Nonnull Store<EntityStore> store) {
        ui.append("Pages/HyColony/Citizen.ui");
        for (int i = 0; i < tabs.size(); i++) {
            bind(events, tabs.get(i).hit, "tab", i);
        }
        if (tabs.contains(Tab.JOB)) {
            ui.set("#JobTab.Visible", true);
            ui.set("#JobHit.Visible", true);
        }
        ui.set(tab.page + ".Visible", true);
        CitizenMainTab.render(ui, view);
        HappinessRowsUi.citizen(ui, "#HappinessPage #HappinessRows", view.happinessRows());
        skills.render(ui, events);
        requests.render(ui, events);
        view.jobSkills().ifPresent(j -> CitizenJobTab.render(ui, view.jobId(), j));
    }

    @Override
    public void handleDataEvent(@Nonnull Ref<EntityStore> ref, @Nonnull Store<EntityStore> store, @Nonnull Act act) {
        if ("tab".equals(act.action())) {
            if (act.index() >= 0 && act.index() < tabs.size()) {
                select(tabs.get(act.index()));
            }
            return;
        }
        if (!requests.handle(ref, store, this, act)) {
            skills.handle(act).ifPresent(update -> sendUpdate(update, null, false));
        }
    }

    /**
     * Shows next. Inventory asks the core, which checks the right (MANAGE_HUTS, as MC) and then opens the citizen's
     * container in place of this window (CitizenInventoryWindows).
     */
    private void select(Tab next) {
        if (next == Tab.INVENTORY) {
            manager.citizenInventories().open(player, view.colonyId(), view.citizenId());
            return;
        }
        tab = next;
        rebuild();
    }
}
