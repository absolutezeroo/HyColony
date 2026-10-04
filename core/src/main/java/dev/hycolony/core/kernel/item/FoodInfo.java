package dev.hycolony.core.kernel.item;

/**
 * What a citizen gets from eating an item: MC FoodProperties.getNutrition, the IMinecoloniesFoodItem tier (0 for an
 * ordinary food, 1 to 3 for a prepared dish) and the MC {@code poisonousfood} tag.
 */
public record FoodInfo(int nutrition, int tier, boolean poisonous) {
    /** The highest dish tier (MC IMinecoloniesFoodItem tiers 1 to 3). */
    public static final int MAX_TIER = 3;

    public FoodInfo {
        if (nutrition < 1) {
            throw new IllegalArgumentException("nutrition must be >= 1: " + nutrition);
        }
        if (tier < 0 || tier > MAX_TIER) {
            throw new IllegalArgumentException("tier must be 0 to 3: " + tier);
        }
    }

    /** Whether it is one of the prepared dishes (MC IMinecoloniesFoodItem). */
    public boolean isDish() {
        return tier > 0;
    }
}
