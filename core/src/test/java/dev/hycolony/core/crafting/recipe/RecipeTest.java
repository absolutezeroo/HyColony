package dev.hycolony.core.crafting.recipe;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.hycolony.core.kernel.item.ItemAmount;
import dev.hycolony.core.kernel.item.ItemKey;
import dev.hycolony.core.kernel.item.ToolType;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class RecipeTest {
    static final ItemKey ESSENCE = new ItemKey("Ingredient_Life_Essence");
    static final ItemKey FIBRE = new ItemKey("Ingredient_Fibre");
    static final ItemKey SEED = new ItemKey("Plant_Seeds_Wheat");

    static Recipe recipe(List<Ingredient> in) {
        return new Recipe(
                in,
                new ItemAmount(SEED, 1),
                List.of(),
                new BenchRequirement("Farmingbench", List.of("Seeds"), 1),
                Optional.empty(),
                new RecipeSource.Hytale("Plant_Seeds_Wheat"),
                false);
    }

    @Test
    void cleanedInputMergesEqualIngredients() {
        Recipe r = recipe(List.of(new Ingredient.OfItem(ESSENCE, 1), new Ingredient.OfItem(ESSENCE, 1)));
        assertEquals(List.of(new Ingredient.OfItem(ESSENCE, 2)), r.cleanedInput());
    }

    @Test
    void mergedIngredientMovesToTheEndLikeMc() {
        Recipe r = recipe(List.of(
                new Ingredient.OfItem(ESSENCE, 1), new Ingredient.OfItem(FIBRE, 3), new Ingredient.OfItem(ESSENCE, 2)));
        assertEquals(List.of(new Ingredient.OfItem(FIBRE, 3), new Ingredient.OfItem(ESSENCE, 3)), r.cleanedInput());
    }

    @Test
    void anItemAndAResourceTypeOfTheSameNameStayApart() {
        Recipe r = recipe(List.of(new Ingredient.OfItem(ESSENCE, 1), new Ingredient.OfResourceType(ESSENCE.id(), 1)));
        assertEquals(r.inputs(), r.cleanedInput());
    }

    @Test
    void sameContentIgnoresTheSource() {
        Recipe a = recipe(List.of(new Ingredient.OfItem(ESSENCE, 2)));
        Recipe b = new Recipe(
                a.inputs(),
                a.primaryOutput(),
                a.secondaryOutputs(),
                a.bench(),
                a.requiredTool(),
                new RecipeSource.Custom("x"),
                false);
        assertTrue(a.sameContentAs(b));
    }

    @Test
    void sameContentComparesTheCleanedInput() {
        Recipe split = recipe(List.of(new Ingredient.OfItem(ESSENCE, 1), new Ingredient.OfItem(ESSENCE, 1)));
        Recipe merged = recipe(List.of(new Ingredient.OfItem(ESSENCE, 2)));
        Recipe more = recipe(List.of(new Ingredient.OfItem(ESSENCE, 3)));
        assertTrue(split.sameContentAs(merged));
        assertFalse(merged.sameContentAs(more));
    }

    @Test
    void anotherOutputCountBenchOrToolIsAnotherRecipe() {
        Recipe a = recipe(List.of(new Ingredient.OfItem(ESSENCE, 2)));
        Recipe twoSeeds = new Recipe(
                a.inputs(), new ItemAmount(SEED, 2), List.of(), a.bench(), Optional.empty(), a.source(), false);
        Recipe tier2 = new Recipe(
                a.inputs(),
                a.primaryOutput(),
                List.of(),
                new BenchRequirement("Farmingbench", List.of("Seeds"), 2),
                Optional.empty(),
                a.source(),
                false);
        Recipe withAxe = new Recipe(
                a.inputs(), a.primaryOutput(), List.of(), a.bench(), Optional.of(ToolType.AXE), a.source(), false);
        assertFalse(a.sameContentAs(twoSeeds));
        assertFalse(a.sameContentAs(tier2));
        assertFalse(a.sameContentAs(withAxe));
    }

    @Test
    void ingredientAmountMustBePositive() {
        assertThrows(IllegalArgumentException.class, () -> new Ingredient.OfItem(ESSENCE, 0));
        assertThrows(IllegalArgumentException.class, () -> new Ingredient.OfTag("Wood", -1));
    }

    @Test
    void fieldcraftIsTheHandBench() {
        assertTrue(new BenchRequirement(BenchRequirement.FIELDCRAFT, List.of("Tools"), 0).isFieldcraft());
        assertFalse(new BenchRequirement("Farmingbench", List.of(), 1).isFieldcraft());
    }

    @Test
    void withSourceKeepsTheContent() {
        Recipe a = recipe(List.of(new Ingredient.OfItem(ESSENCE, 2)));
        Recipe custom = a.withSource(new RecipeSource.Custom("gift"));

        assertEquals(new RecipeSource.Custom("gift"), custom.source());
        assertTrue(custom.sameContentAs(a));
    }

    @Test
    void improvedWithTakesTheNewInputsAndNoSource() {
        Recipe a = new Recipe(
                List.of(new Ingredient.OfItem(ESSENCE, 2)),
                new ItemAmount(SEED, 1),
                List.of(new ItemAmount(FIBRE, 1)),
                new BenchRequirement("Farmingbench", List.of("Seeds"), 1),
                Optional.of(ToolType.AXE),
                new RecipeSource.Hytale("Plant_Seeds_Wheat"),
                true);

        Recipe improved = a.improvedWith(List.of(new Ingredient.OfItem(ESSENCE, 1)));

        assertEquals(List.of(new Ingredient.OfItem(ESSENCE, 1)), improved.inputs());
        assertEquals(new RecipeSource.Improved(), improved.source());
        assertEquals(a.primaryOutput(), improved.primaryOutput());
        assertEquals(a.secondaryOutputs(), improved.secondaryOutputs());
        assertEquals(a.bench(), improved.bench());
        assertEquals(a.requiredTool(), improved.requiredTool());
        assertTrue(improved.knowledgeRequired());
    }
}
