package dev.hycolony.core.citizen.inventory;

/**
 * MC ItemStackUtils.getArmorLevel: an armour piece's level, the first reference piece it does not exceed (MC
 * EquipmentLevelConstants: leather 0, gold 1, chain 2, iron 3, diamond 4), else {@link #ABOVE_ALL}.
 *
 * <p>Deviation from MC: Hytale has no armour value; a piece's ItemLevel is compared to the reference pieces' (light
 * leather 15, iron 20, bronze 25, thorium 30, adamantite 40), which stand for MC's leather, gold, chain, iron and
 * diamond (spec 2026-10-02 citizen inventory, § 3).
 */
public final class ArmorLevels {
    /** MC: above every reference piece. */
    public static final int ABOVE_ALL = 5;

    private static final int[] REFERENCE_ITEM_LEVELS = {15, 20, 25, 30, 40};

    private ArmorLevels() {}

    /** The MC armour level of a piece of {@code itemLevel}, 0 to {@link #ABOVE_ALL}. */
    public static int of(int itemLevel) {
        for (int level = 0; level < REFERENCE_ITEM_LEVELS.length; level++) {
            if (itemLevel <= REFERENCE_ITEM_LEVELS[level]) {
                return level;
            }
        }
        return ABOVE_ALL;
    }
}
