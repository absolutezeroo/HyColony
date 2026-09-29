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
import dev.hycolony.plugin.ui.hut.HutTab;
import dev.hycolony.plugin.ui.hut.HutTabs;
import java.util.ArrayList;
import java.util.List;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;

/**
 * A hut's window, in tabs as MC AbstractBuildingWindow: Main, then one tab per module view of the hut (MC module
 * order), each appended from its own {@code .ui} into {@code #ModuleTabs}. Vanilla tab pattern
 * (TriggerVolumeInspectorPage): a {@code #TabButtons} row, the active tab disabled, one content group per tab shown or
 * hidden. The open tab is page state, kept across the core's re-shows.
 */
public final class BuildingPage extends ColonyPage {
    private static final String MODULE_TABS = "#ModuleTabs";

    /** A tab button: its content group and its label key. */
    private record Bar(String group, String labelKey) implements TabBar.Tab {}

    private final BuildingView view;
    private final Runnable pickUp;
    private final HutStorage storage;
    private final BuildingMainTab main;
    private final List<HutTab> moduleTabs;
    private final List<Bar> bar = new ArrayList<>();
    /** Index in {@link #bar}; 0 is Main. */
    private int tab;

    public BuildingPage(PlayerRef playerRef, BuildingView view, ColonyManager manager, Runnable pickUp) {
        super(playerRef, manager);
        this.view = view;
        this.pickUp = pickUp;
        this.storage = new HutStorage(playerRef, manager, view.pos());
        this.main = new BuildingMainTab(manager, player, view);
        this.moduleTabs = HutTabs.of(view, manager, player);
        bar.add(new Bar("#MainTab", "hycolony.ui.building.tab.main"));
        for (int i = 0; i < moduleTabs.size(); i++) {
            bar.add(new Bar(root(i), moduleTabs.get(i).labelKey()));
        }
    }

    private static String root(int moduleTab) {
        return MODULE_TABS + "[" + moduleTab + "]";
    }

    /** The view drawn, to tell whose window this is. */
    public BuildingView view() {
        return view;
    }

    /** Opens on the tab {@code previous} showed if it is this hut's window (the core re-shows after each action). */
    public BuildingPage keepTabOf(@Nullable CustomUIPage previous) {
        if (previous instanceof BuildingPage p && p.view.pos().equals(view.pos()) && p.tab < bar.size()) {
            tab = p.tab;
        }
        return this;
    }

    /**
     * For the core's live refresh: {@link #keepTabOf} plus the Build options (and its chosen style) and inventory
     * summary sub-views, which an action's re-show resets.
     */
    public BuildingPage keepStateOf(@Nullable CustomUIPage previous) {
        keepTabOf(previous);
        if (previous instanceof BuildingPage p && p.view.pos().equals(view.pos())) {
            main.keepStateOf(p.main);
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
        // Appended before the tab row, which shows or hides each tab's root.
        for (HutTab t : moduleTabs) {
            ui.append(MODULE_TABS, t.document());
        }
        TabBar.render(ui, events, bar, bar.get(tab));
        main.render(ui, events, storage.mayOpen());
        for (int i = 0; i < moduleTabs.size(); i++) {
            moduleTabs.get(i).render(ui, events, root(i));
        }
    }

    @Override
    public void handleDataEvent(@Nonnull Ref<EntityStore> ref, @Nonnull Store<EntityStore> store, @Nonnull Act act) {
        switch (act.action) {
            case "tab" -> {
                if (act.index >= 0 && act.index < bar.size()) {
                    tab = act.index;
                    rebuild();
                }
            }
            case "storage" -> storage.open(ref, store);
            case "pickUp" -> pickUp.run();
            default -> {
                if (main.handle(act)) {
                    rebuild();
                }
                moduleTabs.forEach(t -> t.handle(act));
                moduleTabs.stream()
                        .flatMap(t -> t.picker(act).stream())
                        .findFirst()
                        .ifPresent(picker -> openPicker(ref, store, picker));
                if (moduleTabs.stream().anyMatch(t -> t.redraws(act))) {
                    rebuild();
                }
            }
        }
    }

    /** Replaces this window with a tab's item list (MC WindowSelectRes); an offline player gets nothing. */
    private void openPicker(Ref<EntityStore> ref, Store<EntityStore> store, ItemPickerPage.Picker picker) {
        Player p = store.getComponent(ref, Player.getComponentType());
        if (p != null) {
            p.getPageManager().openCustomPage(ref, store, new ItemPickerPage(playerRef, manager, picker));
        }
    }
}
