package dev.hycolony.core.testing;

import dev.hycolony.core.crafting.recipe.Ingredient;
import dev.hycolony.core.crafting.recipe.Recipe;
import dev.hycolony.core.crafting.recipe.RecipeCatalog;
import dev.hycolony.core.crafting.recipe.RecipeSource;
import dev.hycolony.core.kernel.item.ItemAmount;
import dev.hycolony.core.kernel.item.ItemKey;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/** Recipes, item groups and bench data set by the test; anything unknown answers empty, like the real port. */
public final class FakeRecipeCatalog implements RecipeCatalog {
    public final List<Recipe> recipes = new ArrayList<>();
    public final Map<String, List<ItemKey>> resourceTypes = new HashMap<>();
    public final Map<String, List<ItemKey>> tags = new HashMap<>();
    /** Cost of reaching a tier from the one below, keyed {@code bench + ":" + tier}. */
    public final Map<String, List<ItemAmount>> upgradeCosts = new HashMap<>();

    public final Map<String, ItemKey> benchItems = new HashMap<>();
    /** A bench's own categories; a bench absent here accepts every category. */
    public final Map<String, List<String>> benchCategories = new HashMap<>();
    /** Hytale recipe ids each player knows. */
    public final Map<UUID, Set<String>> knownByPlayer = new HashMap<>();

    public FakeRecipeCatalog add(Recipe recipe) {
        recipes.add(recipe);
        return this;
    }

    public FakeRecipeCatalog resourceType(String id, ItemKey... items) {
        resourceTypes.put(id, List.of(items));
        return this;
    }

    public FakeRecipeCatalog tag(String id, ItemKey... items) {
        tags.put(id, List.of(items));
        return this;
    }

    @Override
    public List<Recipe> all() {
        return List.copyOf(recipes);
    }

    @Override
    public Optional<Recipe> byHytaleId(String id) {
        return recipes.stream()
                .filter(r -> r.source() instanceof RecipeSource.Hytale(String hytaleId) && hytaleId.equals(id))
                .findFirst();
    }

    @Override
    public List<ItemKey> itemsOf(Ingredient ingredient) {
        return switch (ingredient) {
            case Ingredient.OfItem i -> List.of(i.item());
            case Ingredient.OfResourceType t -> resourceTypes.getOrDefault(t.id(), List.of());
            case Ingredient.OfTag t -> tags.getOrDefault(t.id(), List.of());
        };
    }

    @Override
    public List<ItemAmount> benchUpgradeCost(String benchId, int fromTier, int toTier) {
        Map<ItemKey, Integer> sum = new LinkedHashMap<>();
        for (int tier = fromTier + 1; tier <= toTier; tier++) {
            for (ItemAmount a : upgradeCosts.getOrDefault(benchId + ":" + tier, List.of())) {
                sum.merge(a.item(), a.count(), Integer::sum);
            }
        }
        List<ItemAmount> out = new ArrayList<>();
        sum.forEach((item, count) -> out.add(new ItemAmount(item, count)));
        return out;
    }

    @Override
    public Optional<ItemKey> benchItem(String benchId) {
        return Optional.ofNullable(benchItems.get(benchId));
    }

    @Override
    public List<String> benchCategories(String benchId) {
        return benchCategories.getOrDefault(benchId, List.of());
    }

    @Override
    public boolean playerKnows(UUID player, String hytaleRecipeId) {
        return knownByPlayer.getOrDefault(player, Set.of()).contains(hytaleRecipeId);
    }
}
