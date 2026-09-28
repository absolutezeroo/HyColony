package dev.hycolony.core.crafting.module;

import com.google.gson.JsonObject;
import dev.hycolony.core.building.Building;
import dev.hycolony.core.building.CreatesResolvers;
import dev.hycolony.core.building.PersistentModule;
import dev.hycolony.core.building.ProvidesTab;
import dev.hycolony.core.building.TickingModule;
import dev.hycolony.core.colony.Colony;
import dev.hycolony.core.colony.ui.tab.ModuleTab;
import dev.hycolony.core.crafting.recipe.Recipe;
import dev.hycolony.core.crafting.recipe.RecipeId;
import dev.hycolony.core.crafting.recipe.RecipeSource;
import dev.hycolony.core.crafting.request.CraftingResolvers;
import dev.hycolony.core.kernel.port.ItemCatalog;
import dev.hycolony.core.logistics.pickup.KeepRule;
import dev.hycolony.core.logistics.pickup.KeepsItems;
import dev.hycolony.core.request.Resolver;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Predicate;
import org.jspecify.annotations.Nullable;

/**
 * A hut's crafting module (MC AbstractCraftingBuildingModule): the recipes it learnt, by id in the colony registry, in
 * the order its crafters try them, and those disabled.
 *
 * <p>Deviation from MC: a recipe is taught by choosing it in the recipes tab, not by laying it out in a grid; research
 * is not ported, so its {@code RECIPES} effect is 0. A bad index or a recipe missing from the list changes nothing,
 * where MC throws or clears the list.
 */
public final class CraftingModule
        implements PersistentModule, TickingModule, CreatesResolvers, KeepsItems, ProvidesTab {
    /** MC AbstractCraftingBuildingModule.EXTRA_RECIPE_MULTIPLIER. */
    static final int EXTRA_RECIPE_MULTIPLIER = 5;

    /** Why a recipe cannot be taught to the hut, in the order they are checked. */
    public enum LearnRefusal {
        /** MC hasSpaceForMoreRecipes is false. */
        FULL,
        /** MC isRecipeCompatibleWithCraftingModule is false, or the registry does not know the recipe. */
        INCOMPATIBLE,
        /** A Hytale recipe reserved to the players who learnt it, and the teaching player did not. */
        UNKNOWN_TO_PLAYER;

        /** The lang key the Recipes tab shows for this refusal: {@code hycolony.ui.recipes.refused.<name>}. */
        public String langKey() {
            return "hycolony.ui.recipes.refused." + name().toLowerCase(Locale.ROOT);
        }
    }

    private final String jobId;
    private final boolean canLearnManyRecipes;
    private final RecipeList list = new RecipeList();

    /** A module for {@code jobId}'s crafters; {@code canLearnManyRecipes} is false for MC SimpleCraftingModule. */
    public CraftingModule(String jobId, boolean canLearnManyRecipes) {
        this.jobId = jobId;
        this.canLearnManyRecipes = canLearnManyRecipes;
    }

    /** The job whose crafters use these recipes (MC jobEntry), and whose {@code crafting.json} rules apply. */
    public String jobId() {
        return jobId;
    }

    /** The learnt recipes, in the order crafters try them; read-only. */
    public List<RecipeId> recipes() {
        return list.ids();
    }

    public boolean isDisabled(RecipeId id) {
        return list.isDisabled(id);
    }

    /**
     * MC holdsRecipe: the recipe is listed and enabled. MC also matches the single-output form of a listed multi-output
     * recipe; Hytale has none.
     */
    public boolean holdsRecipe(RecipeId id) {
        return list.ids().contains(id) && !list.isDisabled(id);
    }

    /** Whether the recipe is a custom one: granted by the hut level, not counted in the maximum, not removable. */
    public boolean isCustom(Colony colony, RecipeId id) {
        return colony.recipes()
                .get(id)
                .map(r -> r.source() instanceof RecipeSource.Custom)
                .orElse(false);
    }

    /** MC getMaxRecipes: 2 to the hut level, times {@link #EXTRA_RECIPE_MULTIPLIER} unless a simple module. */
    public int maxRecipes(Building hut) {
        double increase = 1;
        if (canLearnManyRecipes) {
            increase *= EXTRA_RECIPE_MULTIPLIER;
        }
        return (int) (Math.pow(2, hut.level()) * increase);
    }

    /** MC getActiveRecipes: the enabled recipes a player taught (or a crafter improved), not the custom ones. */
    public int activeRecipes(Colony colony) {
        return (int) list.ids().stream()
                .filter(id -> !list.isDisabled(id))
                .filter(id -> colony.recipes().get(id).isPresent() && !isCustom(colony, id))
                .count();
    }

    /** MC canRecipeBeAdded: why {@code player} may not teach the hut the recipe; empty if they may. */
    public Optional<LearnRefusal> canLearn(Colony colony, Building hut, RecipeId id, UUID player) {
        return refusal(colony, hut, colony.recipes().get(id).orElse(null), player);
    }

    /** {@link #canLearn} for a recipe the registry may not know yet; a null recipe is unknown, so INCOMPATIBLE. */
    Optional<LearnRefusal> refusal(Colony colony, Building hut, @Nullable Recipe recipe, UUID player) {
        if (maxRecipes(hut) <= activeRecipes(colony)) {
            return Optional.of(LearnRefusal.FULL);
        }
        if (recipe == null || !RecipeCompatibility.compatible(colony, hut, jobId, recipe)) {
            return Optional.of(LearnRefusal.INCOMPATIBLE);
        }
        if (!RecipeCompatibility.knownBy(colony, recipe, player)) {
            return Optional.of(LearnRefusal.UNKNOWN_TO_PLAYER);
        }
        return Optional.empty();
    }

    /**
     * MC addRecipe: adds the recipe at the end of the list (once) and marks the colony dirty, then, if the hut has
     * workers, wakes the requests its output may serve; false, changing nothing, if {@link #canLearn} refuses.
     */
    public boolean learn(Colony colony, Building hut, RecipeId id, UUID player) {
        if (canLearn(colony, hut, id, player).isPresent()) {
            return false;
        }
        list.add(id, false);
        colony.markDirty();
        if (!AssignedCitizens.of(colony, hut).isEmpty()) {
            handleRecipeUpdate(colony, id);
        }
        return true;
    }

    /**
     * MC handleRecipeUpdate: offers again the colony's item requests the recipe's primary output answers (MC also
     * matches the alternate outputs, which Hytale recipes do not have); nothing for a recipe the registry lacks.
     */
    public void handleRecipeUpdate(Colony colony, RecipeId id) {
        ItemCatalog items = colony.requests().catalog();
        colony.recipes()
                .get(id)
                .ifPresent(recipe -> colony.requests()
                        .onColonyUpdate(request -> request.deliverable()
                                .map(d -> d.matches(recipe.primaryOutput(), items))
                                .orElse(false)));
    }

    /**
     * MC removeRecipe: forgets the recipe and its disabled mark, marks the colony dirty and wakes the requests its
     * output served. Deviation from MC: a recipe not in the list changes nothing (false), where MC clears the list.
     */
    public boolean remove(Colony colony, RecipeId id) {
        if (!list.remove(id)) {
            return false;
        }
        colony.markDirty();
        handleRecipeUpdate(colony, id);
        return true;
    }

    /**
     * MC toggle: disables the recipe at {@code index}, or enables it again and wakes the requests its output serves;
     * marks the colony dirty. Deviation from MC: an index outside the list changes nothing, where MC throws.
     */
    public void toggle(Colony colony, int index) {
        if (!list.inRange(index)) {
            return;
        }
        RecipeId id = list.ids().get(index);
        if (list.toggle(id)) {
            handleRecipeUpdate(colony, id);
        }
        colony.markDirty();
    }

    /**
     * MC switchOrder: a full move sends the recipe at {@code i} to the top if {@code i > j}, else to the bottom;
     * otherwise swaps {@code i} and {@code j}; returns false for an index outside the list. Deviation from MC: such an
     * index changes nothing, where MC throws on a full move; a full move marks the colony dirty too (MC's saves do not
     * depend on it).
     */
    public boolean switchOrder(Colony colony, int i, int j, boolean fullMove) {
        if (!list.move(i, j, fullMove)) {
            return false;
        }
        colony.markDirty();
        return true;
    }

    /**
     * MC createResolvers: the hut's crafting resolvers ({@link CraftingResolvers}), for requests and for production,
     * public then private. Deviation from MC: the private ones come from here, where MC's WorkerBuildingModule makes
     * them, so that {@code job} does not depend on {@code crafting}; a hut without crafting module has no private
     * crafting, which no MC job without one uses.
     */
    @Override
    public List<Resolver> createResolvers(Colony colony, Building building) {
        return CraftingResolvers.of(colony, building, jobId);
    }

    /**
     * MC getRequiredItemsAndAmount: the ingredients and outputs of the crafters' pending tasks stay in the hut's racks
     * ({@link RecipeReservations#keepRules}).
     */
    @Override
    public List<KeepRule> keepRules(Colony colony, Building building) {
        return RecipeReservations.keepRules(colony, building, this);
    }

    /** MC serializeToView feeding CraftingModuleView: the hut window's Recipes tab ({@link RecipesTab}). */
    @Override
    public ModuleTab tab(Colony colony, Building building, UUID viewer) {
        return RecipesTab.of(colony, building, this, viewer);
    }

    /** MC onColonyTick: grants and withdraws the custom recipes of the hut's level ({@link CustomRecipes#check}). */
    @Override
    public void onColonyTick(Colony colony, Building building) {
        CustomRecipes.check(colony, building, this);
    }

    /** MC addRecipeToList: adds the recipe first or last, unless already listed; does not mark the colony dirty. */
    void addRecipeToList(RecipeId id, boolean atTop) {
        list.add(id, atTop);
    }

    /**
     * MC replaceRecipe: puts {@code newId} in the place of {@code oldId} and marks the colony dirty; no-op if
     * {@code oldId} is not listed. Deviation from MC: if {@code newId} is already listed, {@code oldId} is only removed,
     * where MC lists {@code newId} twice.
     */
    void replaceRecipe(Colony colony, RecipeId oldId, RecipeId newId) {
        if (list.replace(oldId, newId)) {
            colony.markDirty();
        }
    }

    /**
     * MC serializeToView's removal of a listed recipe the manager lost, done by the load's repair: keeps the recipes
     * {@code keep} accepts and forgets the others with their disabled mark; returns whether any went. Does not mark the
     * colony dirty, the caller does.
     */
    public boolean retainRecipes(Predicate<RecipeId> keep) {
        return list.retain(keep);
    }

    @Override
    public void write(JsonObject out) {
        list.write(out);
    }

    /** MC deserializeNBT; tolerant, see {@link RecipeList#read}. */
    @Override
    public void read(JsonObject in) {
        list.read(in);
    }
}
