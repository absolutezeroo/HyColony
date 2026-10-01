package dev.hycolony.plugin.ui.hut.annex;

import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.ui.builder.UICommandBuilder;
import com.hypixel.hytale.server.core.ui.builder.UIEventBuilder;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import dev.hycolony.core.app.ColonyManager;
import dev.hycolony.core.app.action.CraftingActions;
import dev.hycolony.core.app.ui.BuildingView;
import dev.hycolony.core.crafting.module.RecipesView;
import dev.hycolony.core.kernel.BlockPos;
import dev.hycolony.plugin.ui.BuildingPage;
import dev.hycolony.plugin.ui.ColonyPage;
import dev.hycolony.plugin.ui.hut.HutWindow;
import dev.hycolony.plugin.ui.hut.RecipeLines;
import java.util.List;
import javax.annotation.Nonnull;

/**
 * The recipes a crafting hut could learn, opened by Teach Recipe (HyColony's stand-in for MC's crafting grid, see
 * RecipesView): each recipe as MC's recipe line with Learn, disabled with the reason when refused, and Back. Learn
 * goes to the core (MANAGE_HUTS), which shows the hut again and so redraws this window.
 */
public final class RecipeTeachPage extends ColonyPage implements HutWindow {
    private final BuildingView view;

    public RecipeTeachPage(PlayerRef playerRef, ColonyManager manager, BuildingView view) {
        super(playerRef, manager);
        this.view = view;
    }

    @Override
    public BlockPos hutPos() {
        return view.pos();
    }

    @Override
    public ColonyPage with(PlayerRef playerRef, BuildingView fresh) {
        return new RecipeTeachPage(playerRef, manager, fresh);
    }

    private List<RecipesView.Line> learnable() {
        return view.tab(RecipesView.class).map(RecipesView::learnable).orElse(List.of());
    }

    @Override
    public void build(
            @Nonnull Ref<EntityStore> ref,
            @Nonnull UICommandBuilder ui,
            @Nonnull UIEventBuilder events,
            @Nonnull Store<EntityStore> store) {
        ui.append("Pages/HyColony/RecipeTeach.ui");
        bind(events, "#Back", "back");
        List<RecipesView.Line> lines = learnable();
        ui.set("#Empty.Visible", lines.isEmpty());
        for (int i = 0; i < lines.size(); i++) {
            RecipesView.Line line = lines.get(i);
            String sel = "#Recipes[" + i + "]";
            ui.append("#Recipes", RecipeLines.DOCUMENT);
            RecipeLines.fill(ui, sel, line);
            for (String button : new String[] {" #Up", " #Down", " #Remove", " #Toggle"}) {
                ui.set(sel + button + ".Visible", false);
            }
            ui.set(sel + " #Learn.Visible", true);
            if (line.refusal().isPresent()) {
                ui.set(sel + " #Learn.Disabled", true);
                ui.set(
                        sel + " #Learn.TooltipText",
                        Message.translation(line.refusal().get()));
            } else {
                bindRef(events, sel + " #Learn", "learn", line.recipeId());
            }
        }
    }

    @Override
    public void handleDataEvent(@Nonnull Ref<EntityStore> ref, @Nonnull Store<EntityStore> store, @Nonnull Act act) {
        switch (act.action()) {
            case "learn" ->
                learnable().stream()
                        .filter(l -> l.recipeId().equals(act.ref()))
                        .findFirst()
                        .ifPresent(l -> new CraftingActions(manager).learn(player, view.pos(), l.recipeId()));
            case "back" -> BuildingPage.back(ref, store, playerRef, view, manager);
            default -> {}
        }
    }
}
