package dev.hycolony.plugin.ui.citizen;

import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.entity.entities.player.pages.CustomUIPage;
import com.hypixel.hytale.server.core.ui.builder.UICommandBuilder;
import com.hypixel.hytale.server.core.ui.builder.UIEventBuilder;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import dev.hyblockui.api.InventoryGrids;
import dev.hycolony.core.app.ColonyManager;
import dev.hycolony.core.app.ui.CitizenView;
import dev.hycolony.plugin.ui.ColonyPage;
import java.util.ArrayList;
import java.util.List;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;

/**
 * The citizen's window as MC's (AbstractWindowCitizen and its pages): the colonist paper, the side tabs of nav.xml in
 * MC's order, Main, Requests, Inventory, then Job for a citizen with a workplace, and the open tab's page. Moving from
 * tab to tab stays in this window (MC opens a window per tab); no tab looks open, as in MC.
 *
 * <p>Deviation from MC: no Happiness, Family nor Debug tab (no such systems yet). The Inventory tab opens the
 * citizen's container straight away as MC's does, but inside this window, over the player's own inventory, where MC
 * opens a separate container screen (see {@link CitizenInventoryPanel}).
 */
public final class CitizenPage extends ColonyPage {
    /** A tab: its page group and the button that opens it (Citizen.ui). */
    enum Tab {
        MAIN("#MainPage", "#MainHit"),
        REQUESTS("#RequestsPage", "#RequestsHit"),
        INVENTORY("#InventoryPage", "#InventoryHit"),
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
    private final List<Tab> tabs = new ArrayList<>(List.of(Tab.MAIN, Tab.REQUESTS, Tab.INVENTORY));
    private Tab tab = Tab.MAIN;
    private @Nullable CitizenInventoryPanel inventory;

    public CitizenPage(PlayerRef playerRef, CitizenView view, ColonyManager manager) {
        super(playerRef, manager);
        this.view = view;
        this.requests = new CitizenRequestsTab(manager, player, view);
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
     * Opens on the tab {@code previous} showed if it is this citizen's window (the core re-shows after actions), taking
     * over its open inventory; an Inventory tab whose container is gone falls back to Main.
     */
    public CitizenPage keepTabOf(@Nullable CustomUIPage previous) {
        if (previous instanceof CitizenPage p && shows(p.view.colonyId(), p.view.citizenId()) && tabs.contains(p.tab)) {
            tab = p.tab;
            inventory = p.inventory;
            p.inventory = null;
            if (inventory != null) {
                inventory.attach(this::redrawIfShown);
            }
            if (inventory != null && !inventory.isOpen()) { // its window closed: nothing left to show
                inventory.stopWatch();
                inventory = null;
            }
            if (tab == Tab.INVENTORY && inventory == null) {
                tab = Tab.MAIN;
            }
        }
        return this;
    }

    /** Whether this is the window of that citizen. */
    boolean shows(int colonyId, int citizenId) {
        return view.colonyId() == colonyId && view.citizenId() == citizenId;
    }

    /** A copy of this window on its Inventory tab, showing panel (whose window opens with it). */
    CitizenPage withInventory(PlayerRef playerRef, CitizenInventoryPanel panel) {
        CitizenPage page = new CitizenPage(playerRef, view, manager);
        page.tab = Tab.INVENTORY;
        page.inventory = panel;
        panel.attach(page::redrawIfShown);
        return page;
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
        skills.render(ui, events);
        requests.render(ui, events);
        if (tab == Tab.INVENTORY && inventory != null) {
            inventory.draw(ui, events, "#PlayerInventory", store, ref);
        }
        view.jobSkills().ifPresent(j -> CitizenJobTab.render(ui, view.jobId(), j));
    }

    @Override
    public void handleDataEvent(@Nonnull Ref<EntityStore> ref, @Nonnull Store<EntityStore> store, @Nonnull Act act) {
        if ("tab".equals(act.action())) {
            if (act.index() >= 0 && act.index() < tabs.size()) {
                select(ref, tabs.get(act.index()));
            }
            return;
        }
        if (InventoryGrids.DROP_ACTION.equals(act.action())) {
            if (inventory != null) {
                inventory.drop(ref, store, act.drop()); // the moves redraw the window
            }
            return;
        }
        skills.handle(act).ifPresent(update -> sendUpdate(update, null, false));
        requests.handle(act);
    }

    /** Stops showing the inventory: its window closes. Never throws. */
    @Override
    public void onDismiss(@Nonnull Ref<EntityStore> ref, @Nonnull Store<EntityStore> store) {
        if (live() instanceof CitizenPage p) {
            p.leaveInventory(ref);
        }
        super.onDismiss(ref, store);
    }

    /**
     * Shows next. The Inventory tab asks the core, which checks the permission and then reopens this window with the
     * citizen's container (CitizenInventoryWindows); leaving it closes the container.
     */
    private void select(Ref<EntityStore> ref, Tab next) {
        if (inventory != null && !inventory.isOpen()) { // its window closed meanwhile: open a new one
            leaveInventory(ref);
        }
        if (next == Tab.INVENTORY && inventory == null) {
            manager.citizenInventories().open(player, view.colonyId(), view.citizenId());
            return;
        }
        if (next != Tab.INVENTORY) {
            leaveInventory(ref);
        }
        tab = next;
        rebuild();
    }

    private void leaveInventory(Ref<EntityStore> ref) {
        CitizenInventoryPanel current = inventory;
        inventory = null;
        if (current != null) {
            current.close(ref);
        }
    }

    /**
     * Redraws while the player still looks at this window, leaving the Inventory tab if the container closed (the
     * colony went, or the client closed it); otherwise does nothing.
     */
    // Identity: the window's live page must be this very page, not an equal one.
    @SuppressWarnings({"PMD.CompareObjectsWithEquals", "ReferenceEquality"})
    private void redrawIfShown() {
        Ref<EntityStore> ref = playerRef.getReference();
        // The redraw runs on the world the window opened in; a player gone to another world is not drawn from here.
        if (ref == null
                || !ref.isValid()
                || !ref.getStore().getExternalData().getWorld().isInThread()) {
            return;
        }
        Player shown = ref.getStore().getComponent(ref, Player.getComponentType());
        if (shown == null
                || !(shown.getPageManager().getCustomPage() instanceof ColonyPage open)
                || open.live() != this) {
            return;
        }
        if (inventory != null && !inventory.isOpen()) {
            leaveInventory(ref);
            tab = Tab.MAIN;
        }
        rebuild();
    }
}
