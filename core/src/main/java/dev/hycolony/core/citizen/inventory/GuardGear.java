package dev.hycolony.core.citizen.inventory;

import dev.hycolony.core.kernel.item.ArmorInfo;

/**
 * MC ContainerCitizenInventory's armour slots (l. 153-162, 255-268, GuardGearBuilder.buildGearForLevel): the work
 * building's level sets the armour levels ({@link ArmorLevels}) a citizen may be given, between MC
 * EquipmentLevelConstants' bounds: level 1 leather to gold (0 to 1), 2 leather to chain (0 to 2), 3 leather to iron
 * (0 to 3), 4 chain to diamond (2 to 4), 5 iron and above (3 and up); nothing without a work building or beyond
 * level 5.
 */
public final class GuardGear {
    /** MC's lowest armour level allowed, by building level 1 to 5. */
    private static final int[] MIN_LEVEL = {0, 0, 0, 2, 3};
    /** MC's highest armour level allowed, by building level 1 to 5 (MC ARMOR_LEVEL_MAX at 5). */
    private static final int[] MAX_LEVEL = {1, 2, 3, 4, Integer.MAX_VALUE};

    private GuardGear() {}

    /**
     * Whether a citizen whose work building is at {@code buildingLevel} (0 without one) may wear {@code piece} in
     * armour {@code slot}: the piece's own slot and an allowed level.
     */
    public static boolean allows(int buildingLevel, ArmorInfo piece, ArmorInfo.Slot slot) {
        if (buildingLevel <= 0 || buildingLevel > MAX_LEVEL.length || piece.slot() != slot) {
            return false;
        }
        int level = ArmorLevels.of(piece.itemLevel());
        return level >= MIN_LEVEL[buildingLevel - 1] && level <= MAX_LEVEL[buildingLevel - 1];
    }
}
