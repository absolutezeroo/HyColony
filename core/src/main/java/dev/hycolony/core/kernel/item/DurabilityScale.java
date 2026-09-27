package dev.hycolony.core.kernel.item;

/**
 * Converts a tool's damage in uses (1 per block, MC) to and from the durability points of the game, where
 * {@code uses} ({@code ItemCatalog.durability}) spend {@code max} points. A partly spent use counts as a whole one, so a
 * round trip never repairs. {@code uses} or {@code max} of 0 or less is unbreakable: no damage.
 */
public final class DurabilityScale {
    /** Floating noise, in uses, so a durability computed from a damage reads back as that same damage. */
    private static final double EPSILON = 1e-9;

    private DurabilityScale() {}

    /** The points left after {@code damage} uses, at least 0; {@code max} when unbreakable. */
    public static double durability(int damage, int uses, double max) {
        if (uses <= 0 || max <= 0) {
            return max;
        }
        return Math.max(0, max - damage * (max / uses));
    }

    /** The uses worn off at {@code durability} points, rounded up and at most {@code uses}; 0 when unbreakable. */
    public static int damage(double durability, int uses, double max) {
        if (uses <= 0 || max <= 0) {
            return 0;
        }
        double worn = (max - durability) / (max / uses);
        return (int) Math.min(uses, Math.max(0, Math.ceil(worn - EPSILON)));
    }
}
