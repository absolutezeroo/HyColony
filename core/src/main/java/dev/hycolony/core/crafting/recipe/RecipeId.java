package dev.hycolony.core.crafting.recipe;

import java.util.Objects;

/**
 * A recipe's key in the colony's recipe registry (MC the {@code IToken} of StandardRecipeManager):
 * {@code hytale:<Hytale recipe id>}, {@code custom:<crafting.json id>} or {@code improved:<n>}.
 */
public record RecipeId(String value) {
    public RecipeId {
        Objects.requireNonNull(value, "value");
    }
}
