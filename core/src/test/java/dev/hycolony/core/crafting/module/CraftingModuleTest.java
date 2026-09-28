package dev.hycolony.core.crafting.module;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.hycolony.core.crafting.module.CraftingModule.LearnRefusal;
import dev.hycolony.core.crafting.recipe.Recipe;
import dev.hycolony.core.crafting.recipe.RecipeFixtures;
import dev.hycolony.core.crafting.recipe.RecipeId;
import dev.hycolony.core.crafting.recipe.RecipeSource;
import dev.hycolony.core.kernel.item.ItemKey;
import dev.hycolony.core.request.Resolver;
import dev.hycolony.core.request.model.RequestToken;
import dev.hycolony.core.request.model.StackRequest;
import dev.hycolony.core.request.resolver.PlayerResolver;
import dev.hycolony.core.request.resolver.RetryingResolver;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class CraftingModuleTest {
    private final CraftingHut h = new CraftingHut();

    /** Teaches {@code n} Fieldcraft recipes making {@code Out_0}, {@code Out_1}...; returns their ids. */
    private List<RecipeId> teachMany(int n) {
        List<RecipeId> ids = new ArrayList<>();
        for (int i = 0; i < n; i++) {
            ids.add(h.teach(RecipeFixtures.fieldcraft("Basic", "Out_" + i)));
        }
        return ids;
    }

    private String resolverOf(RequestToken token) {
        return h.colony.requests().resolverOf(token).map(Resolver::resolverId).orElseThrow();
    }

    @Test
    void maxRecipesIsTwoToTheLevelTimesFive() {
        assertEquals(10, h.module.maxRecipes(h.hut));
        h.hut.setLevel(3);
        assertEquals(40, h.module.maxRecipes(h.hut));
    }

    @Test
    void simpleModuleLearnsTwoToTheLevel() {
        CraftingHut simple = new CraftingHut(CraftingHut.RULES, false);
        simple.hut.setLevel(2);
        assertEquals(4, simple.module.maxRecipes(simple.hut));
    }

    @Test
    void learnRefusedWhenFull() {
        teachMany(10);
        RecipeId eleventh = h.register(RecipeFixtures.fieldcraft("Basic", "Out_10"));

        assertEquals(Optional.of(LearnRefusal.FULL), h.module.canLearn(h.colony, h.hut, eleventh, h.owner));
        assertFalse(h.module.learn(h.colony, h.hut, eleventh, h.owner));
        assertEquals(10, h.module.recipes().size());
    }

    @Test
    void learningAddsAtTheEndAndMarksTheColonyDirty() {
        List<RecipeId> ids = teachMany(2);
        h.colony.clearDirty();

        RecipeId third = h.teach(RecipeFixtures.fieldcraft("Basic", "Out_2"));

        assertEquals(List.of(ids.get(0), ids.get(1), third), h.module.recipes());
        assertTrue(h.colony.isDirty());
    }

    @Test
    void learningAKnownRecipeAgainKeepsOneEntry() {
        RecipeId id = h.teach(RecipeFixtures.fieldcraft("Basic", "Out"));

        assertTrue(h.module.learn(h.colony, h.hut, id, h.owner), "MC addRecipe: true, the list is unchanged");
        assertEquals(List.of(id), h.module.recipes());
    }

    @Test
    void customRecipesDoNotCountTowardsTheMaximum() {
        teachMany(9);
        RecipeId custom = h.register(
                RecipeFixtures.from(RecipeFixtures.fieldcraft("Basic", "Gift"), new RecipeSource.Custom("gift")));
        h.module.addRecipeToList(custom, false);

        assertTrue(h.module.isCustom(h.colony, custom));
        assertEquals(9, h.module.activeRecipes(h.colony));
        assertEquals(Optional.empty(), h.module.canLearn(h.colony, h.hut, h.register(fieldcraft(9)), h.owner));
    }

    @Test
    void disabledRecipesDoNotCountTowardsTheMaximum() {
        List<RecipeId> ids = teachMany(10);
        h.module.toggle(h.colony, 0);

        assertEquals(9, h.module.activeRecipes(h.colony));
        assertTrue(h.module.learn(h.colony, h.hut, h.register(fieldcraft(10)), h.owner));
        assertEquals(11, h.module.recipes().size());
        assertTrue(h.module.isDisabled(ids.getFirst()));
    }

    @Test
    void toggleDisablesThenEnables() {
        RecipeId id = h.teach(RecipeFixtures.fieldcraft("Basic", "Out"));
        h.colony.clearDirty();

        h.module.toggle(h.colony, 0);
        assertTrue(h.module.isDisabled(id));
        assertTrue(h.colony.isDirty());

        h.module.toggle(h.colony, 0);
        assertFalse(h.module.isDisabled(id));
        assertEquals(List.of(id), h.module.recipes());
    }

    @Test
    void toggleOutsideTheListChangesNothing() {
        RecipeId id = h.teach(RecipeFixtures.fieldcraft("Basic", "Out"));

        h.module.toggle(h.colony, 1);
        h.module.toggle(h.colony, -1);

        assertFalse(h.module.isDisabled(id));
    }

    @Test
    void switchOrderFullMoveSendsToTop() {
        List<RecipeId> ids = teachMany(4);

        h.module.switchOrder(h.colony, 2, 1, true);

        assertEquals(List.of(ids.get(2), ids.get(0), ids.get(1), ids.get(3)), h.module.recipes());
    }

    @Test
    void switchOrderFullMoveSendsToBottom() {
        List<RecipeId> ids = teachMany(4);
        h.colony.clearDirty();

        h.module.switchOrder(h.colony, 1, 2, true);

        assertEquals(List.of(ids.get(0), ids.get(2), ids.get(3), ids.get(1)), h.module.recipes());
        assertTrue(h.colony.isDirty(), "the new order must be saved");
    }

    @Test
    void switchOrderSwapsTwoRecipes() {
        List<RecipeId> ids = teachMany(3);

        h.module.switchOrder(h.colony, 0, 2, false);

        assertEquals(List.of(ids.get(2), ids.get(1), ids.get(0)), h.module.recipes());
    }

    @Test
    void switchOrderOutsideTheListChangesNothing() {
        List<RecipeId> ids = teachMany(2);

        h.module.switchOrder(h.colony, 0, 2, false);
        h.module.switchOrder(h.colony, 2, 0, true);

        assertEquals(ids, h.module.recipes());
    }

    @Test
    void removeForgetsTheRecipeAndItsDisabledMark() {
        List<RecipeId> ids = teachMany(2);
        h.module.toggle(h.colony, 0);

        assertTrue(h.module.remove(h.colony, ids.getFirst()));

        assertEquals(List.of(ids.get(1)), h.module.recipes());
        assertFalse(h.module.isDisabled(ids.getFirst()));
    }

    @Test
    void removingARecipeNotInTheListKeepsTheOthers() {
        List<RecipeId> ids = teachMany(2);

        assertFalse(h.module.remove(h.colony, new RecipeId("hytale:Unknown")));

        assertEquals(ids, h.module.recipes());
    }

    @Test
    void learningWakesRequestsForItsOutput() {
        h.hire();
        RequestToken wheat = h.colony
                .requests()
                .createAndAssign(h.hut, new StackRequest(new ItemKey("Plant_Seeds_Wheat"), 10, 10, true), -1);
        RequestToken other =
                h.colony.requests().createAndAssign(h.hut, new StackRequest(new ItemKey("Other"), 1, 1, true), -1);
        assertEquals(RetryingResolver.ID, resolverOf(wheat));

        h.teach(RecipeFixtures.fieldcraft("Seeds", "Plant_Seeds_Wheat"));

        assertTrue(
                resolverOf(wheat).startsWith("crafting:public:"),
                "MC handleRecipeUpdate: onColonyUpdate on its output, which the hut can now craft");
        assertEquals(RetryingResolver.ID, resolverOf(other));
    }

    @Test
    void learningWithoutAWorkerWakesNoRequest() {
        RequestToken wheat = h.colony
                .requests()
                .createAndAssign(h.hut, new StackRequest(new ItemKey("Plant_Seeds_Wheat"), 10, 10, true), -1);

        h.teach(RecipeFixtures.fieldcraft("Seeds", "Plant_Seeds_Wheat"));

        assertEquals(RetryingResolver.ID, resolverOf(wheat), "MC addRecipe: no assigned citizen, no update");
    }

    @Test
    void enablingARecipeAgainWakesRequestsForItsOutput() {
        h.teach(RecipeFixtures.fieldcraft("Seeds", "Plant_Seeds_Wheat"));
        h.module.toggle(h.colony, 0);
        RequestToken wheat = h.colony
                .requests()
                .createAndAssign(h.hut, new StackRequest(new ItemKey("Plant_Seeds_Wheat"), 10, 10, true), -1);

        h.module.toggle(h.colony, 0);

        assertEquals(PlayerResolver.ID, resolverOf(wheat), "MC toggle: onColonyUpdate on its output");
    }

    @Test
    void recipesSurviveSaveAndLoad() {
        List<RecipeId> ids = teachMany(3);
        h.module.toggle(h.colony, 1);
        JsonObject saved = new JsonObject();
        h.module.write(saved);

        CraftingModule back = new CraftingModule(CraftingHut.JOB, true);
        back.read(JsonParser.parseString(saved.toString()).getAsJsonObject());

        assertEquals(ids, back.recipes());
        assertTrue(back.isDisabled(ids.get(1)));
        assertFalse(back.isDisabled(ids.get(0)));
    }

    @Test
    void malformedSaveReadsWhatItCan() {
        CraftingModule back = new CraftingModule(CraftingHut.JOB, true);
        back.read(
                JsonParser.parseString("{\"recipes\": [\"hytale:A\", 3, \"hytale:A\", \"hytale:B\"], \"disabled\": 7}")
                        .getAsJsonObject());

        assertEquals(List.of(new RecipeId("hytale:A"), new RecipeId("hytale:B")), back.recipes());
    }

    private static Recipe fieldcraft(int i) {
        return RecipeFixtures.fieldcraft("Basic", "Out_" + i);
    }
}
