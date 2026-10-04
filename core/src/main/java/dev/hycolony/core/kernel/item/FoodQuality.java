package dev.hycolony.core.kernel.item;

/**
 * What a Hytale food without a HyColony food file gives a citizen, by the rank of its Hytale quality (spec 2026-10-04
 * § 5.3): the median nutrition and tier of our food table's foods of that quality. The id-map maps Hytale's quality
 * ids to these ranks, so the core names no quality.
 *
 * <p>Deviation from MC (Hytale world): MC reads an item's FoodProperties → a Hytale food without a HyColony file takes
 * the median value of our table's foods of its Quality.
 */
public enum FoodQuality {
    /** Hytale's Common, and any quality the id-map does not rank: median of the 30 Common foods. */
    COMMON(3, 0),
    /** Hytale's Uncommon: median of the 9 Uncommon foods (nutrition 6 to 9, tiers 1 and 2). */
    UNCOMMON(8, 2),
    /** Hytale's Rare and above: median of the 4 Rare foods. */
    RARE(12, 3);

    private final FoodInfo food;

    FoodQuality(int nutrition, int tier) {
        this.food = new FoodInfo(nutrition, tier, false);
    }

    /** What a food of this rank gives a citizen; never poisonous, as Hytale marks no food so. */
    public FoodInfo food() {
        return food;
    }
}
