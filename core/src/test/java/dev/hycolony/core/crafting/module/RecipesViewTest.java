package dev.hycolony.core.crafting.module;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.hycolony.core.colony.ui.tab.RecipesView;
import dev.hycolony.core.colony.ui.tab.RecipesView.IngredientLine;
import dev.hycolony.core.colony.ui.tab.RecipesView.Line;
import dev.hycolony.core.crafting.recipe.BenchRequirement;
import dev.hycolony.core.crafting.recipe.Ingredient;
import dev.hycolony.core.crafting.recipe.Recipe;
import dev.hycolony.core.crafting.recipe.RecipeFixtures;
import dev.hycolony.core.crafting.recipe.RecipeId;
import dev.hycolony.core.crafting.recipe.RecipeSource;
import dev.hycolony.core.kernel.item.ItemAmount;
import dev.hycolony.core.kernel.item.ItemKey;
import dev.hycolony.core.kernel.item.ToolType;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.Test;

/** The Recipes tab of a crafting hut (MC CraftingModuleView and WindowListRecipes). */
class RecipesViewTest {
    private static final String FULL = "hycolony.ui.recipes.refused.full";
    private static final Recipe WHEAT = RecipeFixtures.at("Farmingbench", "Seeds", "Plant_Seeds_Wheat");
    private static final Recipe CORN = RecipeFixtures.at("Farmingbench", "Seeds", "Plant_Seeds_Corn");
    private static final Recipe BERRY = RecipeFixtures.fieldcraft("Basic", "Plant_Seeds_Berry");
    private static final ItemKey OAK = new ItemKey("Wood_Oak_Trunk");
    private static final ItemKey FIBRE = new ItemKey("Ingredient_Fibre");
    /** Wood trunks by type, essence twice and fibre by tag, at the Farmingbench with an axe. */
    private static final Recipe PLANKS = new Recipe(
            List.of(
                    new Ingredient.OfResourceType("Wood_Trunk", 2),
                    new Ingredient.OfItem(RecipeFixtures.ESSENCE, 1),
                    new Ingredient.OfTag("Fibre", 1),
                    new Ingredient.OfItem(RecipeFixtures.ESSENCE, 1)),
            new ItemAmount(new ItemKey("Wood_Planks"), 4),
            List.of(),
            new BenchRequirement("Farmingbench", List.of("Seeds"), 1),
            Optional.of(ToolType.AXE),
            new RecipeSource.Hytale("Planks"),
            false);

    private final CraftingHut h = new CraftingHut();

    RecipesViewTest() {
        h.bench("Farmingbench", 1);
    }

    private RecipesView view() {
        return (RecipesView) h.module.tab(h.colony, h.hut, h.owner);
    }

    private static List<String> ids(List<Line> lines) {
        return lines.stream().map(Line::recipeId).toList();
    }

    private void teachFieldcraft(int n) {
        for (int i = 0; i < n; i++) {
            h.teach(RecipeFixtures.fieldcraft("Basic", "Out_" + i));
        }
    }

    @Test
    void viewListsLearnableRecipesSortedAndExcludesLearnedOnes() {
        h.t.recipes.add(WHEAT).add(CORN).add(BERRY).add(RecipeFixtures.at("Anvil", "Tools", "Tool_Hammer"));
        RecipeId corn = h.teach(CORN);

        RecipesView v = view();

        assertEquals(List.of(corn.value()), ids(v.learned()));
        assertEquals(
                List.of("hytale:Plant_Seeds_Berry", "hytale:Plant_Seeds_Wheat"),
                ids(v.learnable()),
                "by output id; the Anvil recipe needs a bench the hut does not have");
        assertTrue(v.learnable().stream().allMatch(l -> l.refusal().isEmpty()));
        assertTrue(h.colony.registries().recipes().idOf(WHEAT).isEmpty(), "showing the tab registers nothing");
    }

    @Test
    void recipeOfAHigherBenchTierIsNotLearnable() {
        Recipe tierTwo = new Recipe(
                WHEAT.inputs(),
                WHEAT.primaryOutput(),
                List.of(),
                new BenchRequirement("Farmingbench", List.of("Seeds"), 2),
                Optional.empty(),
                WHEAT.source(),
                false);
        h.t.recipes.add(tierTwo);

        assertEquals(List.of(), view().learnable());

        h.bench("Farmingbench", 2);
        assertEquals(List.of("hytale:Plant_Seeds_Wheat"), ids(view().learnable()));
    }

    @Test
    void fullHutShowsTheRefusalOnLearnableLines() {
        teachFieldcraft(10);
        h.t.recipes.add(WHEAT);

        RecipesView v = view();

        assertEquals(10, v.active());
        assertEquals(10, v.max());
        assertEquals(Optional.of(FULL), v.learnable().getFirst().refusal());
    }

    @Test
    void recipeReservedToKnowingPlayersShowsWhyTheViewerCannotLearnIt() {
        Recipe secret = new Recipe(
                WHEAT.inputs(),
                WHEAT.primaryOutput(),
                List.of(),
                WHEAT.bench(),
                Optional.empty(),
                WHEAT.source(),
                true);
        h.t.recipes.add(secret);

        assertEquals(
                Optional.of("hycolony.ui.recipes.refused.unknown_to_player"),
                view().learnable().getFirst().refusal());

        h.t.recipes.knownByPlayer.put(h.owner, Set.of("Plant_Seeds_Wheat"));
        assertEquals(Optional.empty(), view().learnable().getFirst().refusal());
    }

    @Test
    void learnedLinesFollowTheListOrderWithTheirMarks() {
        RecipeId wheat = h.teach(WHEAT);
        RecipeId berry = h.teach(BERRY);
        RecipeId custom = h.register(RecipeFixtures.from(CORN, new RecipeSource.Custom("corn")));
        h.module.addRecipeToList(custom, false);
        h.module.toggle(h.colony, 1);

        List<Line> learned = view().learned();

        assertEquals(List.of(wheat.value(), berry.value(), custom.value()), ids(learned));
        assertFalse(learned.get(0).disabled());
        assertTrue(learned.get(1).disabled());
        assertFalse(learned.get(0).custom());
        assertTrue(learned.get(2).custom(), "MC: a built-in recipe has no Remove button");
        assertEquals(1, view().active(), "the disabled and the custom recipes do not count");
    }

    @Test
    void lineShowsWhatOneRunMakesAndTakesWithItsBenchAndTool() {
        h.t.recipes.resourceType("Wood_Trunk", OAK, new ItemKey("Wood_Birch_Trunk"));
        h.t.recipes.tag("Fibre", FIBRE);
        h.t.recipes.add(PLANKS);

        Line line = view().learnable().getFirst();

        assertEquals(new ItemAmount(new ItemKey("Wood_Planks"), 4), line.output());
        assertEquals(
                List.of(
                        new IngredientLine(new ItemAmount(OAK, 2), true),
                        new IngredientLine(new ItemAmount(FIBRE, 1), true),
                        new IngredientLine(new ItemAmount(RecipeFixtures.ESSENCE, 2), false)),
                line.inputs(),
                "cleaned input; a type or tag shows its first item");
        assertEquals(Optional.of("Farmingbench"), line.bench());
        assertEquals(Optional.of(ToolType.AXE), line.tool());
    }

    /** A game update left a tag with no item: the learnt recipe, no longer valid, stays listed with the tag's id. */
    @Test
    void learnedLineShowsTheIdOfATagTheGameNoLongerLists() {
        h.t.recipes.resourceType("Wood_Trunk", OAK);
        h.t.recipes.tag("Fibre", FIBRE);
        h.teach(PLANKS);

        h.t.recipes.tags.remove("Fibre");

        assertEquals(
                new IngredientLine(new ItemAmount(new ItemKey("Fibre"), 1), true),
                view().learned().getFirst().inputs().get(1));
    }

    @Test
    void fieldcraftRecipeShowsNoBench() {
        h.t.recipes.add(BERRY);

        assertEquals(Optional.empty(), view().learnable().getFirst().bench());
    }

    @Test
    void disabledTaughtRecipeCannotBeEnabledWhileTheHutIsFull() {
        teachFieldcraft(10);
        RecipeId custom = h.register(RecipeFixtures.from(CORN, new RecipeSource.Custom("corn")));
        h.module.addRecipeToList(custom, false);
        h.module.toggle(h.colony, 0);
        h.module.toggle(h.colony, 10);
        h.teach(WHEAT);

        List<Line> learned = view().learned();

        assertEquals(Optional.of(FULL), learned.get(0).refusal(), "MC hides Enable while full");
        assertEquals(Optional.empty(), learned.get(10).refusal(), "a custom recipe does not count");
        assertEquals(Optional.empty(), learned.get(1).refusal(), "an enabled recipe can always be disabled");
    }
}
