package dev.hycolony.core.kernel.item;

/**
 * What a Hytale food without a HyColony food file gives a citizen, by the rank of its Hytale quality (spec 2026-10-04
 * § 5.3): the median nutrition of our food table's foods of that quality, as an ordinary food (tier 0). The id-map maps
 * Hytale's quality ids to these ranks, so the core names no quality.
 *
 * <p>Deviation from MC (Hytale world): MC reads an item's FoodProperties nutrition → a Hytale food without a HyColony
 * file takes the median nutrition of our table's foods of its Quality. Like MC for another mod's food
 * (FoodUtils.getFoodTier: tier 0 unless nutrition >= 12 and saturation >= 0.8, saturation not ported), it is no dish.
 */
public enum FoodQuality {
    /** Hytale's Common, and any quality the id-map does not rank: median of the 30 Common foods. */
    COMMON(3),
    /** Hytale's Uncommon: median of the 9 Uncommon foods (nutrition 6 to 9). */
    UNCOMMON(8),
    /** Hytale's Rare and above: median of the 4 Rare foods. */
    RARE(12);

    private final FoodInfo food;

    FoodQuality(int nutrition) {
        this.food = new FoodInfo(nutrition, 0, false);
    }

    /**
     * What a food of this rank gives a citizen. Never poisonous: Hytale has no poisonous flag (its toxic foods apply a
     * Poison effect when eaten), so a mod marks a poisonous food with a food file.
     */
    public FoodInfo food() {
        return food;
    }
}
