package dev.hycolony.core.crafting.module;

import dev.hycolony.core.building.Building;
import dev.hycolony.core.citizen.CitizenData;
import dev.hycolony.core.citizen.Skill;
import dev.hycolony.core.colony.Colony;
import dev.hycolony.core.colony.permission.Action;
import dev.hycolony.core.crafting.recipe.CraftingSetup;
import dev.hycolony.core.crafting.recipe.Ingredient;
import dev.hycolony.core.crafting.recipe.Recipe;
import dev.hycolony.core.crafting.recipe.RecipeId;
import dev.hycolony.core.crafting.recipe.RecipeMatching;
import dev.hycolony.core.kernel.item.ItemKey;
import dev.hycolony.core.kernel.port.Msg;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.random.RandomGenerator;

/**
 * MC AbstractCraftingBuildingModule.improveRecipe: after a finished task, a crafter may find how to make a recipe
 * with one less of each reduceable ingredient; the improved recipe takes the place of the original in the list.
 */
public final class RecipeImprovement {
    /** MC BASE_CHANCE: percent per run crafted and per level of the improvement skill. */
    static final double BASE_CHANCE = 0.0625;

    /** MC's cap on the chance, in percent. */
    static final double MAX_CHANCE = 5.0;

    /** MC's {@code RECIPE_IMPROVED} messages, {@code .0} to {@code .2}. */
    static final int MESSAGES = 3;

    private RecipeImprovement() {}

    /**
     * A task just finished: the recipe it used, the runs crafted (MC craftCounter), the crafter and its recipe
     * improvement skill (MC CraftingWorkerBuildingModule.getRecipeImprovementSkill).
     */
    public record Crafted(RecipeId recipe, int count, CitizenData crafter, Skill improvementSkill) {}

    /** The reduced inputs, and the item naming the last ingredient reduced (for the message). */
    record Reduction(List<Ingredient> inputs, ItemKey lastReduced) {}

    /**
     * MC improveRecipe: rolls against {@link #chance}; on success, unless the output is excluded, each reduceable
     * ingredient above 1 loses one, and if the hut may hold the improved recipe it replaces the original and the
     * colony is told. Nothing happens for a recipe the registry lacks.
     */
    public static void improve(Colony colony, Building hut, CraftingModule module, Crafted crafted) {
        improve(colony, hut, module, crafted, colony.context().random());
    }

    /** {@link #improve(Colony, Building, CraftingModule, Crafted)} with a given random source. */
    static void improve(Colony colony, Building hut, CraftingModule module, Crafted crafted, RandomGenerator random) {
        Optional<Recipe> found = colony.registries().recipes().get(crafted.recipe());
        if (found.isEmpty()) {
            return;
        }
        Recipe recipe = found.get();
        CraftingSetup crafting = colony.context().ports().crafting();
        double chance = chance(crafted.count(), crafted.crafter().skills().level(crafted.improvementSkill()));
        double roll = random.nextDouble() * 100;
        if (roll > chance
                || crafting.rules()
                        .isExcludedFromReduction(recipe.primaryOutput().item())) {
            return;
        }
        List<Ingredient> inputs = new ArrayList<>(recipe.cleanedInput());
        inputs.sort(Comparator.comparingInt(Ingredient::amount).reversed());
        Optional<Reduction> reduction = reduce(inputs, crafting);
        if (reduction.isEmpty()) {
            return;
        }
        Recipe improved = recipe.improvedWith(reduction.get().inputs());
        RecipeId id = colony.registries().recipes().checkOrAdd(improved);
        colony.markDirty();
        if (RecipeCompatibility.compatible(colony, hut, module.jobId(), improved)) {
            module.replaceRecipe(colony, crafted.recipe(), id);
            tellMembers(colony, message(module, crafted, recipe, reduction.get(), random));
        }
    }

    /** MC's chance, in percent: {@link #BASE_CHANCE} per run and per skill level, at most {@link #MAX_CHANCE}. */
    static double chance(int count, int skillLevel) {
        return Math.min(MAX_CHANCE, BASE_CHANCE * count + BASE_CHANCE * skillLevel);
    }

    /** MC's reduction loop over {@code inputs}, in order; empty if no ingredient was reduced. */
    static Optional<Reduction> reduce(List<Ingredient> inputs, CraftingSetup crafting) {
        List<Ingredient> out = new ArrayList<>(inputs.size());
        ItemKey lastReduced = null;
        for (Ingredient in : inputs) {
            Optional<ItemKey> item = in.amount() > 1 ? reduceable(in, crafting) : Optional.empty();
            if (item.isPresent()) {
                out.add(in.withAmount(in.amount() - 1));
                lastReduced = item.get();
            } else {
                out.add(in);
            }
        }
        return lastReduced == null ? Optional.empty() : Optional.of(new Reduction(out, lastReduced));
    }

    /**
     * The item naming {@code in} if an improvement may reduce it (MC crafterIngredient reduceable tag): its item when
     * listed. Deviation from MC: a resource type or tag ingredient is reduceable when every item it accepts is listed,
     * and is named by the first; MC checks the one item its grid fixed.
     */
    private static Optional<ItemKey> reduceable(Ingredient in, CraftingSetup crafting) {
        List<ItemKey> items = RecipeMatching.items(in, crafting.catalog());
        if (items.isEmpty() || !items.stream().allMatch(crafting.rules()::isReduceable)) {
            return Optional.empty();
        }
        return Optional.of(items.getFirst());
    }

    /**
     * MC's {@code RECIPE_IMPROVED + random.nextInt(3)}: the job, the output, the reduced ingredient and the crafter.
     * Deviation from MC: items are named by their id, where MC shows their name.
     */
    private static Msg message(
            CraftingModule module, Crafted crafted, Recipe recipe, Reduction reduction, RandomGenerator random) {
        String jobId = crafted.crafter().job().map(j -> j.type().id()).orElse(module.jobId());
        return Msg.of(
                "hycolony.crafting.improved." + random.nextInt(MESSAGES),
                "%hycolony.ui.job." + jobId.substring(jobId.indexOf(':') + 1),
                recipe.primaryOutput().item().id(),
                reduction.lastReduced().id(),
                crafted.crafter().name());
    }

    /** MC sendTo(colony).forAllPlayers: the members allowed to receive colony messages. */
    private static void tellMembers(Colony colony, Msg message) {
        for (UUID member : colony.permissions().members().keySet()) {
            if (colony.permissions().hasPermission(member, Action.RECEIVE_MESSAGES)) {
                colony.context().notifier().send(member, message);
            }
        }
    }
}
