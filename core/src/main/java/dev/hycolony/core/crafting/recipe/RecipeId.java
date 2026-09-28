package dev.hycolony.core.crafting.recipe;

import java.util.Objects;
import java.util.Optional;

/**
 * A recipe's key in the colony's recipe registry (MC the {@code IToken} of StandardRecipeManager):
 * {@code hytale:<Hytale recipe id>}, {@code custom:<crafting.json id>} or {@code improved:<n>}.
 */
public record RecipeId(String value) {
    private static final String HYTALE = "hytale:";

    public RecipeId {
        Objects.requireNonNull(value, "value");
    }

    /** The id {@link RecipeRegistry#checkOrAdd} gives a Hytale recipe taught by hand: {@code hytale:<id>}. */
    public static RecipeId hytale(String hytaleRecipeId) {
        return new RecipeId(HYTALE + hytaleRecipeId);
    }

    /** The Hytale recipe id a {@code hytale:<id>} names; empty for any other id. */
    public Optional<String> hytaleId() {
        return value.startsWith(HYTALE) ? Optional.of(value.substring(HYTALE.length())) : Optional.empty();
    }
}
