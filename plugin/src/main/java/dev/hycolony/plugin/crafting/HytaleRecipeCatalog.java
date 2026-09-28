package dev.hycolony.plugin.crafting;

import com.hypixel.hytale.logger.HytaleLogger;
import com.hypixel.hytale.server.core.asset.type.item.config.CraftingRecipe;
import dev.hycolony.core.crafting.recipe.Ingredient;
import dev.hycolony.core.crafting.recipe.Recipe;
import dev.hycolony.core.crafting.recipe.RecipeCatalog;
import dev.hycolony.core.kernel.item.ItemAmount;
import dev.hycolony.core.kernel.item.ItemKey;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;
import java.util.UUID;
import java.util.logging.Level;

/**
 * RecipeCatalog over the Hytale asset maps (plugin-b-api § « Recettes et tables »). Everything is read once, by
 * {@link #load()}, which must run before the colonies load: a load drops every learnt recipe the catalog does not know.
 * An asset reload needs a restart, like {@code HytaleItemCatalog}. Never throws.
 */
public final class HytaleRecipeCatalog implements RecipeCatalog {
    private static final HytaleLogger LOG = HytaleLogger.forEnclosingClass();

    private final Map<String, Recipe> recipes;
    private final List<Recipe> all;
    private final ResourceTypeIndex resourceTypes;
    private final BenchIndex benches;
    private boolean warned;

    private HytaleRecipeCatalog(Map<String, Recipe> recipes, ResourceTypeIndex resourceTypes, BenchIndex benches) {
        this.recipes = recipes;
        this.all = List.copyOf(recipes.values());
        this.resourceTypes = resourceTypes;
        this.benches = benches;
    }

    /** The game's catalog; {@link RecipeCatalog#NONE} if the assets cannot be read (logged). */
    public static RecipeCatalog load() {
        try {
            Map<String, Recipe> recipes = new TreeMap<>();
            SkippedAssets skipped = new SkippedAssets("recipe");
            for (CraftingRecipe r : CraftingRecipe.getAssetMap().getAssetMap().values()) {
                try {
                    if (r != null && r.getId() != null) {
                        RecipeConversion.convert(r).ifPresent(recipe -> recipes.put(r.getId(), recipe));
                    }
                } catch (RuntimeException e) {
                    skipped.skip(r == null ? "?" : r.getId(), e);
                }
            }
            HytaleRecipeCatalog catalog = new HytaleRecipeCatalog(recipes, ResourceTypeIndex.load(), BenchIndex.load());
            LOG.at(Level.INFO).log("Recipe catalog: %d craftable recipes", recipes.size());
            return catalog;
        } catch (RuntimeException e) {
            LOG.at(Level.SEVERE).withCause(e).log("Recipe catalog could not be read; huts learn no recipe");
            return RecipeCatalog.NONE;
        }
    }

    @Override
    public List<Recipe> all() {
        return all;
    }

    @Override
    public Optional<Recipe> byHytaleId(String id) {
        return Optional.ofNullable(recipes.get(id));
    }

    /** A tag answers nothing: {@link RecipeConversion} never makes a tag ingredient. */
    @Override
    public List<ItemKey> itemsOf(Ingredient ingredient) {
        return switch (ingredient) {
            case Ingredient.OfItem i -> List.of(i.item());
            case Ingredient.OfResourceType t -> resourceTypes.items(t.id());
            case Ingredient.OfTag _ -> List.of();
        };
    }

    @Override
    public List<ItemAmount> benchUpgradeCost(String benchId, int fromTier, int toTier) {
        try {
            return benches.upgradeCost(benchId, fromTier, toTier, resourceTypes::items);
        } catch (RuntimeException e) {
            fail("benchUpgradeCost", e);
            return List.of();
        }
    }

    /**
     * Always empty: several blocks share a bench id ({@code Bench_Farming}, {@code Bench_Trough}), so the plan's own
     * block item is the bench's item (EntryCost falls back to it).
     */
    @Override
    public Optional<ItemKey> benchItem(String benchId) {
        return Optional.empty();
    }

    @Override
    public List<String> benchCategories(String benchId) {
        return benches.categories(benchId);
    }

    /**
     * Hytale keeps a player's known recipes by primary output item (CraftingManager.isValidBenchForRecipe). False when
     * the recipe is unknown or the player offline. World thread: the player's store is read.
     */
    @Override
    public boolean playerKnows(UUID player, String hytaleRecipeId) {
        try {
            Recipe recipe = recipes.get(hytaleRecipeId);
            return recipe != null
                    && KnownRecipes.knows(player, recipe.primaryOutput().item().id());
        } catch (RuntimeException e) {
            fail("playerKnows", e);
            return false;
        }
    }

    private void fail(String op, RuntimeException e) {
        LOG.at(warned ? Level.FINE : Level.WARNING).withCause(e).log("RecipeCatalog.%s failed", op);
        warned = true;
    }
}
