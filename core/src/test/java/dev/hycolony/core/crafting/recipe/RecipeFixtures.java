package dev.hycolony.core.crafting.recipe;

import dev.hycolony.core.kernel.item.ItemAmount;
import dev.hycolony.core.kernel.item.ItemKey;
import java.util.List;
import java.util.Optional;

/** Small Hytale recipes for tests: 2 life essence make 1 {@code output}; the Hytale recipe id is the output id. */
public final class RecipeFixtures {
    public static final ItemKey ESSENCE = new ItemKey("Ingredient_Life_Essence");

    private RecipeFixtures() {}

    /** Made at {@code bench} (tier 1), in one {@code category}. */
    public static Recipe at(String bench, String category, String output) {
        return make(new BenchRequirement(bench, List.of(category), 1), output);
    }

    /** Made by hand, in one {@code category}. */
    public static Recipe fieldcraft(String category, String output) {
        return make(new BenchRequirement(BenchRequirement.FIELDCRAFT, List.of(category), 0), output);
    }

    /** A recipe a crafter improved (no source), made by hand: 1 life essence makes 1 {@code output}. */
    public static Recipe improved(String output) {
        return new Recipe(
                List.of(new Ingredient.OfItem(ESSENCE, 1)),
                new ItemAmount(new ItemKey(output), 1),
                List.of(),
                new BenchRequirement(BenchRequirement.FIELDCRAFT, List.of(), 0),
                Optional.empty(),
                new RecipeSource.Improved(),
                false);
    }

    /** {@code recipe} with another source, same content. */
    public static Recipe from(Recipe recipe, RecipeSource source) {
        return new Recipe(
                recipe.inputs(),
                recipe.primaryOutput(),
                recipe.secondaryOutputs(),
                recipe.bench(),
                recipe.requiredTool(),
                source,
                recipe.knowledgeRequired());
    }

    private static Recipe make(BenchRequirement bench, String output) {
        return new Recipe(
                List.of(new Ingredient.OfItem(ESSENCE, 2)),
                new ItemAmount(new ItemKey(output), 1),
                List.of(),
                bench,
                Optional.empty(),
                new RecipeSource.Hytale(output),
                false);
    }
}
