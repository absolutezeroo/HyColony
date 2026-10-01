package dev.hycolony.plugin.ui;

import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.entity.entities.player.pages.CustomUIPage;
import com.hypixel.hytale.server.core.ui.builder.UICommandBuilder;
import com.hypixel.hytale.server.core.ui.builder.UIEventBuilder;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import dev.hycolony.core.app.ColonyManager;
import dev.hycolony.core.app.ui.BuildingView;
import dev.hycolony.core.kernel.BlockPos;
import dev.hycolony.plugin.ui.hut.HutTab;
import dev.hycolony.plugin.ui.hut.HutTabs;
import dev.hycolony.plugin.ui.hut.HutWindow;
import dev.hycolony.plugin.ui.hut.SideTabs;
import dev.hycolony.plugin.ui.hut.annex.AssignCitizenPage;
import dev.hycolony.plugin.ui.hut.annex.HireWorkerPage;
import dev.hycolony.plugin.ui.hut.annex.HutInfoPage;
import dev.hycolony.plugin.ui.hut.annex.HutInventoryPage;
import dev.hycolony.plugin.ui.hut.annex.HutRenamePage;
import dev.hycolony.plugin.ui.hut.main.MainTab;
import java.util.ArrayList;
import java.util.List;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;

/**
 * A hut's window as MC AbstractBuildingWindow: the builder paper, Main then one side tab per module view of the hut
 * (MC module order), and only the open tab's {@code .ui} appended into {@code #Page}. The open tab is page state,
 * kept across the core's re-shows. Main's buttons may open another window in this one's place.
 */
public final class BuildingPage extends ColonyPage implements HutWindow {
    private static final String PAGE = "#Page[0]";

    private final BuildingView view;
    private final HutStorage storage;
    private final MainTab main;
    private final List<HutTab> moduleTabs;
    /** 0 is Main, then the index in {@link #moduleTabs} plus one. */
    private int tab;

    public BuildingPage(PlayerRef playerRef, BuildingView view, ColonyManager manager) {
        super(playerRef, manager);
        this.view = view;
        this.storage = new HutStorage(playerRef, manager, view.pos());
        this.main = new MainTab(manager, player, view);
        this.moduleTabs = HutTabs.of(view, manager, player);
    }

    /** The view drawn, to tell whose window this is. */
    public BuildingView view() {
        return view;
    }

    @Override
    public BlockPos hutPos() {
        return view.pos();
    }

    @Override
    public ColonyPage with(PlayerRef playerRef, BuildingView fresh) {
        return new BuildingPage(playerRef, fresh, manager).keepTabOf(this);
    }

    /** Opens on the tab {@code previous} showed if it is this hut's window (the core re-shows after each action). */
    public BuildingPage keepTabOf(@Nullable CustomUIPage previous) {
        if (previous instanceof BuildingPage p && p.view.pos().equals(view.pos()) && p.tab <= moduleTabs.size()) {
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
        if (tab == 0) {
            ui.append("#Page", main.document());
            main.render(ui, events, PAGE);
        } else {
            HutTab open = moduleTabs.get(tab - 1);
            ui.append("#Page", open.document());
            ui.set(PAGE + ".Visible", true);
            open.render(ui, events, PAGE);
        }
        List<SideTabs.Tab> tabs = new ArrayList<>();
        tabs.add(new SideTabs.Tab("main", "hycolony.ui.building.tab.main"));
        moduleTabs.forEach(t -> tabs.add(new SideTabs.Tab(t.icon(), t.descKey())));
        SideTabs.render(ui, events, view.pos(), tabs);
    }

    @Override
    public void handleDataEvent(@Nonnull Ref<EntityStore> ref, @Nonnull Store<EntityStore> store, @Nonnull Act act) {
        if ("tab".equals(act.action)) {
            if (act.index >= 0 && act.index <= moduleTabs.size()) {
                tab = act.index;
                rebuild();
            }
            return;
        }
        if (tab == 0) {
            main.handle(act).ifPresent(annex -> open(ref, store, annex));
            return;
        }
        HutTab open = moduleTabs.get(tab - 1);
        open.handle(act);
        open.picker(act).ifPresent(picker -> show(ref, store, new ItemPickerPage(playerRef, manager, picker)));
        if (open.redraws(act)) {
            rebuild();
        }
    }

    /** Opens the window Main asked for in place of this one. */
    private void open(Ref<EntityStore> ref, Store<EntityStore> store, MainTab.Annex annex) {
        switch (annex) {
            case INVENTORY -> storage.open(ref, store);
            case RENAME -> show(ref, store, new HutRenamePage(playerRef, manager, view.pos(), view.customName()));
            case INFO -> show(ref, store, new HutInfoPage(playerRef, manager, view.pos(), view.typeId()));
            case INVENTORY_SUMMARY -> show(ref, store, new HutInventoryPage(playerRef, manager, view));
            case HIRE -> show(ref, store, new HireWorkerPage(playerRef, manager, view));
            case ASSIGN -> show(ref, store, new AssignCitizenPage(playerRef, manager, view));
        }
    }

    /** Replaces this window with {@code page}; an offline player gets nothing. */
    private void show(Ref<EntityStore> ref, Store<EntityStore> store, CustomUIPage page) {
        Player p = store.getComponent(ref, Player.getComponentType());
        if (p != null) {
            p.getPageManager().openCustomPage(ref, store, page);
        }
    }
}
