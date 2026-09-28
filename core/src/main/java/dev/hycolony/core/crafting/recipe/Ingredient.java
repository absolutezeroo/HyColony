package dev.hycolony.core.crafting.recipe;

import dev.hycolony.core.kernel.item.ItemKey;
import java.util.Objects;

/**
 * One input of a recipe and how many of it one run takes (MC the {@code ItemStorage} of RecipeStorage.input).
 * Deviation from MC: a Hytale recipe may ask for any item of a resource type or tag, where the MC grid fixed the exact
 * item when the recipe was taught.
 */
public sealed interface Ingredient {
    /** How many one run of the recipe takes; always > 0. */
    int amount();

    /** The same item, type or tag, {@code newAmount} of it. */
    Ingredient withAmount(int newAmount);

    /** Whether both ask for the same item, type or tag, whatever their amounts (MC ItemStorage.equals). */
    default boolean sameThingAs(Ingredient other) {
        return withAmount(1).equals(other.withAmount(1));
    }

    private static int positive(int amount) {
        if (amount <= 0) {
            throw new IllegalArgumentException("amount must be > 0: " + amount);
        }
        return amount;
    }

    /** One precise item. */
    record OfItem(ItemKey item, int amount) implements Ingredient {
        public OfItem {
            Objects.requireNonNull(item, "item");
            positive(amount);
        }

        @Override
        public OfItem withAmount(int newAmount) {
            return new OfItem(item, newAmount);
        }
    }

    /** Any item of a Hytale resource type ("a wood trunk"). */
    record OfResourceType(String id, int amount) implements Ingredient {
        public OfResourceType {
            Objects.requireNonNull(id, "id");
            positive(amount);
        }

        @Override
        public OfResourceType withAmount(int newAmount) {
            return new OfResourceType(id, newAmount);
        }
    }

    /** Any item carrying a Hytale item tag. */
    record OfTag(String id, int amount) implements Ingredient {
        public OfTag {
            Objects.requireNonNull(id, "id");
            positive(amount);
        }

        @Override
        public OfTag withAmount(int newAmount) {
            return new OfTag(id, newAmount);
        }
    }
}
