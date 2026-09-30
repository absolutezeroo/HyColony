package dev.hycolony.core.app.action;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.JsonParser;
import dev.hycolony.core.app.ColonyManager;
import dev.hycolony.core.app.ui.BuildingView;
import dev.hycolony.core.building.Building;
import dev.hycolony.core.colony.Colony;
import dev.hycolony.core.colony.permission.Permissions;
import dev.hycolony.core.construction.hut.ConstructionBuildingTypes;
import dev.hycolony.core.crafting.module.CraftingModule;
import dev.hycolony.core.crafting.module.RecipesView;
import dev.hycolony.core.crafting.recipe.CraftingRules;
import dev.hycolony.core.crafting.recipe.Recipe;
import dev.hycolony.core.crafting.recipe.RecipeFixtures;
import dev.hycolony.core.crafting.recipe.RecipeId;
import dev.hycolony.core.kernel.BlockPos;
import dev.hycolony.core.kernel.port.Msg;
import dev.hycolony.core.testing.FakeNotifier;
import dev.hycolony.core.testing.TestContexts;
import dev.hycolony.core.testing.crafting.TestCrafters;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/**
 * A crafting hut's Recipes tab buttons (MC AddRemoveRecipeMessage, ToggleRecipeMessage, ChangeRecipePriorityMessage).
 */
class CraftingActionsTest {
    private static final BlockPos HUT = new BlockPos(10, 64, 0);
    private static final Recipe WHEAT = RecipeFixtures.fieldcraft("Seeds", "Plant_Seeds_Wheat");
    private static final RecipeId WHEAT_ID = new RecipeId("hytale:Plant_Seeds_Wheat");
    private static final RecipeId CUSTOM = new RecipeId("custom:corn");
    /** The test crafter learns any Fieldcraft recipe and gets the corn recipe from hut level 1. */
    private static final String RULES = """
            {"jobs": {"%s": {
                "allow": [{"bench": "Fieldcraft", "categories": ["*"]}],
                "custom": [{"id": "corn", "hytaleRecipe": "Plant_Seeds_Corn",
                            "minBuildingLevel": 1, "maxBuildingLevel": 5}]}}}""".formatted(TestCrafters.ID);

    private final TestContexts t = new TestContexts();
    private final UUID alice = UUID.randomUUID();
    private final UUID carol = UUID.randomUUID(); // friend: may look, not manage
    private final ColonyManager manager;
    private final Colony colony;
    private final Building hut;
    private final CraftingModule module;
    private final CraftingActions crafting;

    CraftingActionsTest() {
        t.extraBuildingTypes.add(TestCrafters.HUT);
        t.craftingRules = CraftingRules.parse(JsonParser.parseString(RULES).getAsJsonObject(), w -> {});
        t.recipes.add(WHEAT).add(RecipeFixtures.fieldcraft("Seeds", "Plant_Seeds_Corn"));
        manager = t.manager();
        crafting = new CraftingActions(manager);
        manager.foundation().begin(alice, "Alice", new BlockPos(0, 64, 0), 0);
        colony = manager.foundation().confirm(alice, "A").orElseThrow();
        assertTrue(manager.administration().setRank(alice, colony.id(), carol, "Carol", Permissions.FRIEND));
        manager.huts().place(colony, TestCrafters.HUT.id(), HUT, 0, UUID.randomUUID());
        hut = colony.buildings().at(HUT).orElseThrow();
        hut.setLevel(1);
        hut.setBuilt(true);
        module = hut.module(CraftingModule.class).orElseThrow();
        t.ui.shown.clear();
        t.notifier.sent.clear();
    }

    private RecipesView shownTab(UUID player) {
        BuildingView view = assertInstanceOf(BuildingView.class, t.ui.shown.get(player), "window re-shown");
        return view.tab(RecipesView.class).orElseThrow();
    }

    private List<Msg> messages() {
        return t.notifier.sent.stream().map(FakeNotifier.Sent::msg).toList();
    }

    /** Has Alice teach {@code n} Fieldcraft recipes making {@code Out_0}, {@code Out_1}...; returns their ids. */
    private List<RecipeId> teachMany(int n) {
        for (int i = 0; i < n; i++) {
            t.recipes.add(RecipeFixtures.fieldcraft("Basic", "Out_" + i));
            assertTrue(crafting.learn(alice, HUT, "hytale:Out_" + i));
        }
        return List.copyOf(module.recipes());
    }

    /** MC checkForWorkerSpecificRecipes on the colony tick: the hut level grants the custom corn recipe. */
    private void grantCustomRecipe() {
        module.onColonyTick(colony, hut);
        assertTrue(module.recipes().contains(CUSTOM));
    }

    @Test
    void playerWithoutManageHutsCannotLearn() {
        assertFalse(crafting.learn(carol, HUT, WHEAT_ID.value()));

        assertEquals(List.of(), module.recipes());
        assertTrue(colony.registries().recipes().idOf(WHEAT).isEmpty());
        assertEquals(List.of(), t.notifier.sent);
        assertFalse(t.ui.shown.containsKey(carol));
    }

    @Test
    void learnReShowsTheWindow() {
        assertTrue(crafting.learn(alice, HUT, WHEAT_ID.value()));

        assertEquals(List.of(WHEAT_ID), module.recipes());
        assertEquals(
                List.of(WHEAT_ID.value()),
                shownTab(alice).learned().stream()
                        .map(RecipesView.Line::recipeId)
                        .toList());
        assertEquals(List.of(Msg.of("hycolony.crafting.learned")), messages(), "MC MESSAGE_RECIPE_SAVED");
    }

    @Test
    void refusedLearnTellsWhyAndChangesNothing() {
        teachMany(10);
        t.notifier.sent.clear();

        assertFalse(crafting.learn(alice, HUT, WHEAT_ID.value()));

        assertEquals(10, module.recipes().size());
        assertTrue(colony.registries().recipes().idOf(WHEAT).isEmpty(), "a refused recipe is not registered");
        assertEquals(
                List.of(Msg.of("hycolony.crafting.learnRefused", "%hycolony.ui.recipes.refused.full")), messages());
        assertEquals(10, shownTab(alice).active());
    }

    @Test
    void recipeTheGameDoesNotHaveCannotBeLearnt() {
        assertFalse(crafting.learn(alice, HUT, "hytale:Gone"));

        assertEquals(List.of(), module.recipes());
        assertEquals(
                List.of(Msg.of("hycolony.crafting.learnRefused", "%hycolony.ui.recipes.refused.incompatible")),
                messages());
    }

    @Test
    void customRecipeCannotBeRemoved() {
        grantCustomRecipe();
        assertTrue(crafting.learn(alice, HUT, WHEAT_ID.value()));
        t.ui.shown.clear();

        assertFalse(crafting.remove(alice, HUT, CUSTOM.value()));
        assertEquals(List.of(CUSTOM, WHEAT_ID), module.recipes());
        assertInstanceOf(BuildingView.class, t.ui.shown.get(alice), "the window is re-shown anyway");

        assertFalse(crafting.remove(carol, HUT, WHEAT_ID.value()));
        assertTrue(crafting.remove(alice, HUT, WHEAT_ID.value()));
        assertEquals(List.of(CUSTOM), module.recipes());
        assertFalse(crafting.remove(alice, HUT, WHEAT_ID.value()), "no longer listed");
    }

    @Test
    void enablingATaughtRecipeIsRefusedWhileTheHutIsFull() {
        List<RecipeId> ids = teachMany(10);
        assertTrue(crafting.toggle(alice, HUT, 0));
        assertTrue(module.isDisabled(ids.getFirst()));
        assertTrue(crafting.learn(alice, HUT, WHEAT_ID.value()), "the disabled one does not count");

        assertFalse(crafting.toggle(alice, HUT, 0), "MC hides Enable while full");
        assertTrue(module.isDisabled(ids.getFirst()));

        assertTrue(crafting.toggle(alice, HUT, 1), "disabling is always allowed");
        assertTrue(crafting.toggle(alice, HUT, 0));
        assertFalse(module.isDisabled(ids.getFirst()));
    }

    @Test
    void customRecipeCanBeEnabledWhileTheHutIsFull() {
        grantCustomRecipe();
        assertTrue(crafting.toggle(alice, HUT, 0));
        teachMany(10);

        assertTrue(crafting.toggle(alice, HUT, 0), "MC shows Enable on a built-in recipe");
        assertFalse(module.isDisabled(CUSTOM));
    }

    @Test
    void toggleNeedsManageHutsAndAnIndexInTheList() {
        teachMany(1);

        assertFalse(crafting.toggle(carol, HUT, 0));
        assertFalse(crafting.toggle(alice, HUT, 5));
        assertFalse(crafting.toggle(alice, HUT, -1));
        assertFalse(module.isDisabled(module.recipes().getFirst()));
    }

    @Test
    void moveSwapsWithTheNeighbourOrSendsToTheEnd() {
        List<RecipeId> ids = teachMany(3);

        assertTrue(crafting.move(alice, HUT, 1, true, false));
        assertEquals(List.of(ids.get(1), ids.get(0), ids.get(2)), module.recipes());
        assertInstanceOf(BuildingView.class, t.ui.shown.get(alice));

        assertTrue(crafting.move(alice, HUT, 0, false, true), "MC shift: to the bottom");
        assertEquals(List.of(ids.get(0), ids.get(2), ids.get(1)), module.recipes());

        assertTrue(crafting.move(alice, HUT, 2, true, true), "MC shift: to the top");
        assertEquals(List.of(ids.get(1), ids.get(0), ids.get(2)), module.recipes());

        assertFalse(crafting.move(alice, HUT, 0, true, false), "nothing above the first");
        assertFalse(crafting.move(carol, HUT, 1, true, false));
        assertEquals(List.of(ids.get(1), ids.get(0), ids.get(2)), module.recipes());
    }

    @Test
    void hutWithoutCraftingModuleRefusesEveryButton() {
        BlockPos builder = new BlockPos(20, 64, 0);
        manager.huts().place(colony, ConstructionBuildingTypes.BUILDER.id(), builder, 0, UUID.randomUUID());

        assertFalse(crafting.learn(alice, builder, WHEAT_ID.value()));
        assertFalse(crafting.remove(alice, builder, WHEAT_ID.value()));
        assertFalse(crafting.toggle(alice, builder, 0));
        assertFalse(crafting.move(alice, builder, 0, false, false));
        assertEquals(List.of(), t.notifier.sent);
    }
}
