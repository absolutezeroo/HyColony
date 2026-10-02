package dev.hycolony.core.citizen.inventory;

/**
 * MC ItemStackUtils.getArmorLevel: an armour piece's level is that of the first reference piece of its slot it does
 * not exceed (leather 1, chain 2, iron 3, diamond 4), else {@link #ABOVE_ALL} (netherite).
 *
 * <p>Deviation from MC (Hytale world): MC compares armour values with vanilla's leather, chain, iron and diamond pieces
 * → a piece's ItemLevel compared with Hytale's light leather (15), bronze (25), thorium (30) and adamantite (40)
 * (Server/Item/Items/Armor/*, spec 2026-10-02 citizen inventory § 3). Hytale has no enchantment to add to it.
 */
public final class ArmorLevels {
    /** MC: above every reference piece. */
    public static final int ABOVE_ALL = 5;

    /** The reference pieces' ItemLevels, for MC levels 1 to 4. */
    private static final int[] REFERENCE_ITEM_LEVELS = {15, 25, 30, 40};

    private ArmorLevels() {}

    /** The MC armour level of a piece of {@code itemLevel}, 1 to {@link #ABOVE_ALL}. */
    public static int of(int itemLevel) {
        for (int i = 0; i < REFERENCE_ITEM_LEVELS.length; i++) {
            if (itemLevel <= REFERENCE_ITEM_LEVELS[i]) {
                return i + 1;
            }
        }
        return ABOVE_ALL;
    }
}
