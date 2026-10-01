package dev.hycolony.plugin.ui.hut;

import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.ui.builder.UICommandBuilder;
import com.hypixel.hytale.server.core.ui.builder.UIEventBuilder;
import dev.hycolony.core.app.ColonyManager;
import dev.hycolony.core.app.action.CraftingActions;
import dev.hycolony.core.crafting.module.RecipesView;
import dev.hycolony.core.kernel.BlockPos;
import dev.hycolony.core.kernel.item.ItemAmount;
import dev.hycolony.plugin.crafting.BenchItems;
import dev.hycolony.plugin.ui.ColonyPage;
import java.util.List;
import java.util.UUID;

/**
 * A crafting hut's Recipes tab (MC WindowListRecipes): {@code active} of {@code max}, the learnt recipes with Up, Down,
 * Enable/Disable and Remove, then the recipes the hut could learn with Learn. Each button goes to the core, which
 * checks MANAGE_HUTS and shows the window again.
 *
 * <p>Deviation from MC: Up and Down move one row; MC's Shift-click to the top or bottom has no Hytale event yet.
 */
final class RecipesTab implements HutTab {
    private static final String ROW = "Pages/HyColony/RecipeRow.ui";

    private final ColonyManager manager;
    private final UUID player;
    private final BlockPos hut;
    private final RecipesView recipes;
    private final boolean canManage;

    RecipesTab(ColonyManager manager, UUID player, BlockPos hut, RecipesView recipes, boolean canManage) {
        this.manager = manager;
        this.player = player;
        this.hut = hut;
        this.recipes = recipes;
        this.canManage = canManage;
    }

    @Override
    public String document() {
        return "Pages/HyColony/RecipesTab.ui";
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
                root + " #RecipesCount.Text",
                Message.translation("hycolony.ui.recipes.count")
                        .param("p0", String.valueOf(recipes.active()))
                        .param("p1", String.valueOf(recipes.max())));
        String list = root + " #Recipes";
        int row = header(
                ui, list, 0, "hycolony.ui.recipes.learned", recipes.learned().isEmpty());
        for (int i = 0; i < recipes.learned().size(); i++) {
            String sel = line(ui, list, row++, recipes.learned().get(i));
            learnedButtons(ui, events, sel, recipes.learned().get(i), i);
        }
        row = header(
                ui,
                list,
                row,
                "hycolony.ui.recipes.learnable",
                recipes.learnable().isEmpty());
        for (int i = 0; i < recipes.learnable().size(); i++) {
            String sel = line(ui, list, row++, recipes.learnable().get(i));
            learnableButton(ui, events, sel, recipes.learnable().get(i), i);
        }
    }

    /** A section title, then "None" when the section is empty; returns the next row index. */
    private static int header(UICommandBuilder ui, String list, int row, String key, boolean empty) {
        ui.append(list, "Pages/HyColony/RecipeHeader.ui");
        ui.set(list + "[" + row + "] #Text.Text", Message.translation(key));
        if (!empty) {
            return row + 1;
        }
        ui.append(list, "Pages/HyColony/RecipeHeader.ui");
        ui.set(list + "[" + (row + 1) + "] #Text.Text", Message.translation("hycolony.ui.recipes.none"));
        return row + 2;
    }

    /** Appends one recipe row (output, ingredients, bench, state) and returns its selector. */
    private static String line(UICommandBuilder ui, String list, int row, RecipesView.Line line) {
        String sel = list + "[" + row + "]";
        ui.append(list, ROW);
        ItemAmount out = line.output();
        ui.set(sel + " #Icon.ItemId", out.item().id());
        ui.set(
                sel + " #Title.TextSpans",
                Message.translation("hycolony.ui.recipes.output")
                        .param("p0", String.valueOf(out.count()))
                        .param("p1", ColonyPage.itemName(out.item().id())));
        inputs(ui, sel + " #Inputs", line.inputs());
        ui.set(sel + " #Note.TextSpans", note(line));
        return sel;
    }

    private static void inputs(UICommandBuilder ui, String group, List<RecipesView.IngredientLine> inputs) {
        for (int i = 0; i < inputs.size(); i++) {
            RecipesView.IngredientLine in = inputs.get(i);
            String sel = group + "[" + i + "]";
            ui.append(group, "Pages/HyColony/RecipeInput.ui");
            ui.set(sel + " #Icon.ItemId", in.shown().item().id());
            String key = in.orEquivalent() ? "hycolony.ui.recipes.inputAny" : "hycolony.ui.recipes.input";
            ui.set(
                    sel + " #Count.Text",
                    Message.translation(key)
                            .param("p0", String.valueOf(in.shown().count())));
        }
    }

    /** The bench line, with "Built-in" and "Disabled" marks for learnt lines. */
    private static Message note(RecipesView.Line line) {
        Message bench = line.bench()
                .map(id -> Message.translation("hycolony.ui.recipes.bench")
                        .param("p0", BenchItems.of(id).map(ColonyPage::itemName).orElse(Message.raw(id))))
                .orElse(Message.translation("hycolony.ui.recipes.hand"));
        if (line.custom()) {
            bench = Message.join(bench, Message.raw(" - "), Message.translation("hycolony.ui.recipes.custom"));
        }
        if (line.disabled()) {
            bench = Message.join(bench, Message.raw(" - "), Message.translation("hycolony.ui.recipes.disabled"));
        }
        return bench;
    }

    private void learnedButtons(
            UICommandBuilder ui, UIEventBuilder events, String sel, RecipesView.Line line, int index) {
        ui.set(sel + " #Learn.Visible", false);
        if (!canManage) {
            hide(ui, sel, "#Up", "#Down", "#Toggle", "#Remove");
            return;
        }
        bindOrHide(ui, events, index > 0, new Bind(sel + " #Up", "recipeUp", index));
        bindOrHide(ui, events, index < recipes.learned().size() - 1, new Bind(sel + " #Down", "recipeDown", index));
        bindOrHide(ui, events, line.removable(), new Bind(sel + " #Remove", "recipeRemove", index));
        ui.set(
                sel + " #Toggle.Text",
                Message.translation(line.disabled() ? "hycolony.ui.recipes.enable" : "hycolony.ui.recipes.disable"));
        if (line.refusal().isPresent()) {
            ui.set(sel + " #Toggle.Disabled", true);
            ui.set(
                    sel + " #Toggle.TooltipText",
                    Message.translation(line.refusal().get()));
        } else {
            ColonyPage.bind(events, sel + " #Toggle", "recipeToggle", index);
        }
    }

    private void learnableButton(
            UICommandBuilder ui, UIEventBuilder events, String sel, RecipesView.Line line, int index) {
        hide(ui, sel, "#Up", "#Down", "#Toggle", "#Remove");
        if (!canManage) {
            ui.set(sel + " #Learn.Visible", false);
        } else if (line.refusal().isPresent()) {
            ui.set(sel + " #Learn.Disabled", true);
            ui.set(
                    sel + " #Learn.TooltipText",
                    Message.translation(line.refusal().get()));
        } else {
            ColonyPage.bind(events, sel + " #Learn", "recipeLearn", index);
        }
    }

    /** A button, the action it sends and the row index it sends with it. */
    private record Bind(String button, String action, int index) {}

    /** Binds the button's action when {@code shown}, else hides it. */
    private static void bindOrHide(UICommandBuilder ui, UIEventBuilder events, boolean shown, Bind b) {
        if (shown) {
            ColonyPage.bind(events, b.button(), b.action(), b.index());
        } else {
            ui.set(b.button() + ".Visible", false);
        }
    }

    private static void hide(UICommandBuilder ui, String sel, String... buttons) {
        for (String b : buttons) {
            ui.set(sel + " " + b + ".Visible", false);
        }
    }

    /** Learn takes a learnable row; the others a learnt row. The core re-shows the window. */
    @Override
    public void handle(ColonyPage.Act act) {
        if (!act.action().startsWith("recipe")) {
            return; // BuildingPage offers every action to every tab
        }
        List<RecipesView.Line> rows = act.action().equals("recipeLearn") ? recipes.learnable() : recipes.learned();
        if (act.index() >= 0 && act.index() < rows.size()) {
            run(act.action(), rows.get(act.index()), act.index());
        }
    }

    /** Sends {@code action} for the row {@code line} at {@code index} to the core. */
    private void run(String action, RecipesView.Line line, int index) {
        CraftingActions crafting = new CraftingActions(manager);
        switch (action) {
            case "recipeLearn" -> crafting.learn(player, hut, line.recipeId());
            case "recipeRemove" -> crafting.remove(player, hut, line.recipeId());
            case "recipeToggle" -> crafting.toggle(player, hut, index);
            case "recipeUp" -> crafting.move(player, hut, index, true, false);
            case "recipeDown" -> crafting.move(player, hut, index, false, false);
            default -> {}
        }
    }
}
