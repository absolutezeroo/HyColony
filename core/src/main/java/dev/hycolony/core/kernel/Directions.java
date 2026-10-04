package dev.hycolony.core.kernel;

/** MC BlockPosUtil.calcDirection: where a position lies seen from another, as a {@code hycolony.ui.direction} key. */
public final class Directions {
    /**
     * The directions after north counter-clockwise, each up to its {@link #SECTOR_ENDS} bound in whole degrees (MC
     * calcDirection: 22 < NW < 67 <= W <= 112 < SW < 157...).
     */
    private static final String[] DIRECTIONS = {"nw", "w", "sw", "s", "se", "e", "ne"};

    private static final int[] SECTOR_ENDS = {66, 112, 156, 202, 246, 292, 337};

    private Directions() {}

    /**
     * The direction key of {@code to} seen from {@code from}, north being -z ("n", "ne"...); "up", "down" or "same" on
     * the same column. The angle is truncated to whole degrees before MC's sector bounds.
     */
    public static String of(BlockPos from, BlockPos to) {
        if (to.x() == from.x() && to.z() == from.z()) {
            return to.y() > from.y() ? "up" : to.y() < from.y() ? "down" : "same";
        }
        int degree = (int) (Math.atan2(from.x() - to.x(), from.z() - to.z()) * 180 / Math.PI);
        if (degree < 0) {
            degree += 360;
        }
        if (degree <= 22 || degree >= 338) {
            return "n";
        }
        int i = 0;
        while (degree > SECTOR_ENDS[i]) {
            i++;
        }
        return DIRECTIONS[i];
    }
}
