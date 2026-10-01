package dev.hycolony.core.crafting.restaurant;

import dev.hycolony.core.kernel.item.FoodInfo;
import dev.hycolony.core.kernel.item.Inventory;
import dev.hycolony.core.kernel.item.ItemAmount;
import java.util.Optional;
import java.util.UUID;
import org.jspecify.annotations.Nullable;

/**
 * The meal a waiter gives a player (MC InventoryUtils.transferFoodUpToSaturation): menu food from the waiter's
 * inventory, slot by slot, until its nutrition reaches the required value. Deviation from MC: what the player cannot
 * take goes back to the waiter, where MC swaps it for a non-food item of the player's.
 */
final class PlayerMeal {
    private PlayerMeal() {}

    /** Gives {@code player} menu food worth {@code required} nutrition at most; returns how many items it took. */
    static int give(CookWorkContext ctx, UUID player, int required) {
        Inventory cook = ctx.stock().inventory();
        int found = 0;
        int given = 0;
        for (int i = 0; i < cook.size() && found < required; i++) {
            ItemAmount stack = cook.slot(i).orElse(null);
            Optional<FoodInfo> food = menuFood(ctx, stack);
            if (stack == null || food.isEmpty()) {
                continue;
            }
            int nutrition = food.get().nutrition();
            int amount = (int) Math.ceil((required - found) / (double) nutrition);
            int taken = Math.min(amount, stack.count());
            found = amount > stack.count() ? found + taken * nutrition : required;
            ItemAmount rest = ctx.colony().context().ports().playerInventory().give(player, stack.withCount(taken));
            int took = taken - (rest == null ? 0 : rest.count());
            int left = stack.count() - took;
            cook.set(i, left > 0 ? Optional.of(stack.withCount(left)) : Optional.empty());
            given += took;
        }
        return given;
    }

    /** The food of {@code stack} when it is on the hall's menu; empty for an empty slot or anything else. */
    private static Optional<FoodInfo> menuFood(CookWorkContext ctx, @Nullable ItemAmount stack) {
        return stack == null || !ctx.menu().menu().contains(stack.item())
                ? Optional.empty()
                : ctx.items().food(stack.item());
    }
}
