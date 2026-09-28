package dev.hycolony.core.request.model;

import dev.hycolony.core.kernel.item.ItemKey;
import java.util.Objects;

/**
 * MC PublicCrafting and PrivateCrafting ({@code isPublic}): a crafting task for a hut's crafter, {@code count} runs of
 * the recipe {@code recipeId} (an id of the colony's recipe registry) making {@code stack}, at least {@code minCount}.
 * Not a {@link Deliverable}: no stock resolver serves it, and a courier never carries it.
 *
 * <p>Deviation from MC: two tasks for the same item and counts are equal, whatever their recipe, public or private.
 * MC AbstractCrafting.equals compares the stack, count and minimum count, but the stack through ItemStack.equals,
 * which Minecraft 1.20.1 does not override: two MC tasks are equal only when they share one ItemStack. No effect in
 * game: no rule compares two tasks.
 */
public record Crafting(ItemKey stack, int count, int minCount, String recipeId, boolean isPublic)
        implements Requestable {
    public Crafting {
        Objects.requireNonNull(stack, "stack");
        Objects.requireNonNull(recipeId, "recipeId");
    }

    @Override
    public boolean equals(Object o) {
        return o instanceof Crafting c && count == c.count && minCount == c.minCount && stack.equals(c.stack);
    }

    @Override
    public int hashCode() {
        return Objects.hash(stack, count, minCount);
    }

    @Override
    public String describe() {
        return count + " x craft " + stack.id();
    }
}
