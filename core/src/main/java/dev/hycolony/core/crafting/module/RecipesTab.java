package dev.hycolony.core.crafting.module;

import dev.hycolony.core.building.Building;
import dev.hycolony.core.colony.Colony;
import dev.hycolony.core.colony.ui.tab.RecipesView;
import dev.hycolony.core.colony.ui.tab.RecipesView.IngredientLine;
import dev.hycolony.core.colony.ui.tab.RecipesView.Line;
import dev.hycolony.core.crafting.module.CraftingModule.LearnRefusal;
import dev.hycolony.core.crafting.recipe.Ingredient;
import dev.hycolony.core.crafting.recipe.Recipe;
import dev.hycolony.core.crafting.recipe.RecipeCatalog;
import dev.hycolony.core.crafting.recipe.RecipeId;
import dev.hycolony.core.crafting.recipe.RecipeMatching;
import dev.hycolony.core.crafting.recipe.RecipeSource;
import dev.hycolony.core.kernel.item.ItemAmount;
import dev.hycolony.core.kernel.item.ItemKey;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Builds a crafting module's Recipes tab (MC AbstractCraftingBuildingModule.serializeToView, shown by
 * CraftingModuleView and WindowListRecipes). Reads only: a recipe the hut could learn is registered once learnt.
 */
final class RecipesTab {
    private RecipesTab() {}

    /** The tab of {@code module} in {@code hut}'s window, as {@code viewer} sees it. */
    static RecipesView of(Colony colony, Building hut, CraftingModule module, UUID viewer) {
        return new RecipesView(
                module.activeRecipes(colony),
                module.maxRecipes(hut),
                learned(colony, hut, module),
                learnable(colony, hut, module, viewer));
    }

    /**
     * The listed recipes, in list order, each with why it may not be enabled ({@link RecipeEdits#enableRefusal}). The
     * registry knows them all: it never forgets a recipe, and the load drops the ids a save's registry lost, so a
     * line's position is its index in the list.
     */
    private static List<Line> learned(Colony colony, Building hut, CraftingModule module) {
        RecipeCatalog catalog = colony.context().ports().crafting().catalog();
        List<Line> out = new ArrayList<>();
        for (RecipeId id : module.recipes()) {
            colony.recipes()
                    .get(id)
                    .ifPresent(r -> out.add(new Line(
                            id.value(),
                            r.primaryOutput(),
                            inputs(r, catalog),
                            bench(r),
                            r.requiredTool(),
                            module.isDisabled(id),
                            module.isCustom(colony, id),
                            RecipeEdits.enableRefusal(colony, hut, module, id).map(LearnRefusal::langKey))));
        }
        return out;
    }

    /**
     * What MC's crafting grid offers, as a list: the game's recipes the hut can hold (MC
     * isRecipeCompatibleWithCraftingModule) and has not learnt, by output id, each with why {@code viewer} may not
     * learn it, in MC canRecipeBeAdded's order: the hut is full, then Hytale's knowledge rule.
     */
    private static List<Line> learnable(Colony colony, Building hut, CraftingModule module, UUID viewer) {
        RecipeCatalog catalog = colony.context().ports().crafting().catalog();
        boolean full = module.maxRecipes(hut) <= module.activeRecipes(colony);
        List<Line> out = new ArrayList<>();
        for (Recipe r : catalog.all()) {
            if (!(r.source() instanceof RecipeSource.Hytale(String hytaleId))) {
                continue;
            }
            RecipeId id = colony.recipes().idOf(r).orElseGet(() -> RecipeId.hytale(hytaleId));
            if (module.recipes().contains(id) || !RecipeCompatibility.compatible(colony, hut, module.jobId(), r)) {
                continue;
            }
            Optional<LearnRefusal> refusal = full ? Optional.of(LearnRefusal.FULL) : unknownTo(colony, r, viewer);
            out.add(new Line(
                    id.value(),
                    r.primaryOutput(),
                    inputs(r, catalog),
                    bench(r),
                    r.requiredTool(),
                    false,
                    false,
                    refusal.map(LearnRefusal::langKey)));
        }
        out.sort(Comparator.comparing((Line l) -> l.output().item().id()).thenComparing(Line::recipeId));
        return out;
    }

    /** UNKNOWN_TO_PLAYER if Hytale reserves the recipe to players who learnt it and {@code viewer} did not. */
    private static Optional<LearnRefusal> unknownTo(Colony colony, Recipe r, UUID viewer) {
        return RecipeCompatibility.knownBy(colony, r, viewer)
                ? Optional.empty()
                : Optional.of(LearnRefusal.UNKNOWN_TO_PLAYER);
    }

    /** The bench the recipe is made at; empty for a Fieldcraft recipe, made by hand. */
    private static Optional<String> bench(Recipe r) {
        return r.bench().isFieldcraft()
                ? Optional.empty()
                : Optional.of(r.bench().benchId());
    }

    /** MC WindowListRecipes' input icons, on one line: the cleaned input. */
    private static List<IngredientLine> inputs(Recipe r, RecipeCatalog catalog) {
        return r.cleanedInput().stream().map(in -> ingredient(in, catalog)).toList();
    }

    /** An exact item as is; a resource type or tag by the first item the game lists for it, else by its id. */
    private static IngredientLine ingredient(Ingredient in, RecipeCatalog catalog) {
        return switch (in) {
            case Ingredient.OfItem item -> new IngredientLine(new ItemAmount(item.item(), item.amount()), false);
            case Ingredient.OfResourceType type -> anyOf(in, type.id(), catalog);
            case Ingredient.OfTag tag -> anyOf(in, tag.id(), catalog);
        };
    }

    private static IngredientLine anyOf(Ingredient in, String groupId, RecipeCatalog catalog) {
        ItemKey first = RecipeMatching.items(in, catalog).stream().findFirst().orElseGet(() -> new ItemKey(groupId));
        return new IngredientLine(new ItemAmount(first, in.amount()), true);
    }
}
