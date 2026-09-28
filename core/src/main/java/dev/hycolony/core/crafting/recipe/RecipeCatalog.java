package dev.hycolony.core.crafting.recipe;

import dev.hycolony.core.kernel.item.ItemAmount;
import dev.hycolony.core.kernel.item.ItemKey;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Port: the Hytale recipes and benches (MC IRecipeManager and the vanilla recipe manager). It lives here rather than
 * in {@code kernel/port}, like {@code BlueprintSource}. Never throws: anything unknown answers empty, 0 or false.
 */
public interface RecipeCatalog {
    /** Every Hytale {@code Crafting} and {@code Fieldcraft} recipe; processing benches are left out. */
    List<Recipe> all();

    /** The Hytale recipe with this Hytale id; empty if the game no longer has it. */
    Optional<Recipe> byHytaleId(String id);

    /** The items that answer {@code ingredient}: the item itself, or every item of the resource type or tag. */
    List<ItemKey> itemsOf(Ingredient ingredient);

    /**
     * What raising the bench from {@code fromTier} to {@code toTier} costs: the sum of the Hytale upgrade requirements
     * of each tier above {@code fromTier}; empty if none or unknown.
     */
    List<ItemAmount> benchUpgradeCost(String benchId, int fromTier, int toTier);

    /** The item that places this bench. */
    Optional<ItemKey> benchItem(String benchId);

    /** The bench's own categories; empty = it accepts every category. */
    List<String> benchCategories(String benchId);

    /** Whether the player learnt this Hytale recipe (Hytale {@code KnowledgeRequired}); false if unknown or offline. */
    boolean playerKnows(UUID player, String hytaleRecipeId);
}
