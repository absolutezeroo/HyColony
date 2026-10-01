package dev.hycolony.plugin.ui.hut;

import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.entity.entities.player.pages.CustomUIPage;
import com.hypixel.hytale.server.core.ui.builder.UICommandBuilder;
import com.hypixel.hytale.server.core.ui.builder.UIEventBuilder;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import dev.hycolony.core.app.ColonyManager;
import dev.hycolony.core.app.action.CraftingActions;
import dev.hycolony.core.app.ui.BuildingView;
import dev.hycolony.core.crafting.module.RecipesView;
import dev.hycolony.plugin.ui.ColonyPage;
import dev.hycolony.plugin.ui.hut.annex.RecipeTeachPage;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Function;

/**
 * A crafting hut's recipes page (MC WindowListRecipes): "n of m", the learnt recipes in the order the crafters try them
 * (each with up, down, Remove and Enable/Disable), then Teach Recipe. Each button goes to the core, which checks
 * MANAGE_HUTS and shows the window again; as in MC, a row's event carries its place in the list shown.
 *
 * <p>Deviation from MC: up and down move one row (no Shift to the top or bottom), and a built-in recipe's Remove stays
 * disabled (no Ctrl to force it): Hytale sends no modifier key with a click.
 */
final class RecipesTab implements HutTab {
    private final ColonyManager manager;
    private final UUID player;
    private final BuildingView hut;
    private final RecipesView recipes;

    RecipesTab(ColonyManager manager, UUID player, BuildingView hut, RecipesView recipes) {
        this.manager = manager;
        this.player = player;
        this.hut = hut;
        this.recipes = recipes;
    }

    @Override
    public String document() {
        return "Pages/HyColony/Hut/Recipes.ui";
    }

    @Override
    public String icon() {
        return "crafting";
    }

    @Override
    public String descKey() {
        return "hycolony.ui.building.tab.recipes";
    }

    @Override
    public void render(UICommandBuilder ui, UIEventBuilder events, String root) {
        ui.set(
                root + " #Status.Text",
                Message.translation("hycolony.ui.recipes.count")
                        .param("p0", String.valueOf(recipes.active()))
                        .param("p1", String.valueOf(recipes.max())));
        String list = root + " #Recipes";
        for (int i = 0; i < recipes.learned().size(); i++) {
            RecipesView.Line line = recipes.learned().get(i);
            String sel = list + "[" + i + "]";
            ui.append(list, RecipeLines.DOCUMENT);
            RecipeLines.fill(ui, sel, line);
            buttons(ui, events, sel, line);
        }
        ColonyPage.bind(events, root + " #Teach", "recipeTeach");
    }

    /**
     * MC: up and down; Remove, disabled with MC's tooltip on a built-in recipe; Enable or Disable, Enable hidden while
     * the hut is full (the core's refusal).
     */
    private static void buttons(UICommandBuilder ui, UIEventBuilder events, String sel, RecipesView.Line line) {
        ColonyPage.bindRef(events, sel + " #Up", "recipeUp", line.recipeId());
        ColonyPage.bindRef(events, sel + " #Down", "recipeDown", line.recipeId());
        if (line.removable()) {
            ColonyPage.bindRef(events, sel + " #Remove", "recipeRemove", line.recipeId());
        } else {
            ui.set(sel + " #Remove.Disabled", true);
            ui.set(sel + " #Remove.TooltipText", Message.translation("hycolony.ui.recipes.removeBuiltin"));
        }
        ui.set(
                sel + " #Toggle.Text",
                Message.translation(line.disabled() ? "hycolony.ui.recipes.enable" : "hycolony.ui.recipes.disable"));
        if (line.refusal().isPresent()) {
            ui.set(sel + " #Toggle.Visible", false);
        } else {
            ColonyPage.bindRef(events, sel + " #Toggle", "recipeToggle", line.recipeId());
        }
    }

    /** The learnt row's buttons, found by their recipe; the core re-shows the window. */
    @Override
    public void handle(ColonyPage.Act act) {
        List<RecipesView.Line> learned = recipes.learned();
        for (int i = 0; i < learned.size(); i++) {
            if (learned.get(i).recipeId().equals(act.ref())) {
                run(act.action(), learned.get(i), i);
                return;
            }
        }
    }

    private void run(String action, RecipesView.Line line, int index) {
        CraftingActions crafting = new CraftingActions(manager);
        switch (action) {
            case "recipeRemove" -> crafting.remove(player, hut.pos(), line.recipeId());
            case "recipeToggle" -> crafting.toggle(player, hut.pos(), index);
            case "recipeUp" -> crafting.move(player, hut.pos(), index, true, false);
            case "recipeDown" -> crafting.move(player, hut.pos(), index, false, false);
            default -> {}
        }
    }

    /** Teach Recipe opens the list of recipes to learn (MC: a crafting grid). */
    @Override
    public Optional<Function<PlayerRef, CustomUIPage>> opens(ColonyPage.Act act) {
        return "recipeTeach".equals(act.action())
                ? Optional.of(pr -> new RecipeTeachPage(pr, manager, hut))
                : Optional.empty();
    }
}
