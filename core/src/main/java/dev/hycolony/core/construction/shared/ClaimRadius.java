package dev.hycolony.core.construction.shared;

import dev.hycolony.core.building.BuildingTypes;

/** Claim-square radius by building type and level (Global Constraints claim tables). Level 0 is always 0. */
public final class ClaimRadius {
    // Index = level (0..5).
    private static final int[] TOWN_HALL = {0, 1, 1, 2, 3, 5};
    private static final int[] DEFAULT = {0, 1, 1, 1, 2, 2};

    private ClaimRadius() {}

    public static int of(String buildingTypeId, int level) {
        if (level <= 0) {
            return 0;
        }
        int[] table = BuildingTypes.TOWN_HALL.id().equals(buildingTypeId) ? TOWN_HALL : DEFAULT;
        return table[Math.min(level, table.length - 1)];
    }
}
