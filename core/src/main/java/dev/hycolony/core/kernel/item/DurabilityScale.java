package dev.hycolony.core.kernel.item;

/**
 * Converts a tool's damage in uses (1 per block, MC) to and from the durability points of the game, where
 * {@code uses} ({@code ItemCatalog.durability}) spend the item's max points. A partly spent use counts as a whole one,
 * so a round trip never repairs. An armour piece's uses are its hits. An item the core does not wear ({@code uses} of 0
 * or less: a weapon) counts one use per point, so its wear travels too; a max of 0 or less is unbreakable, with no
 * damage.
 */
public final class DurabilityScale {
    /** Floating noise, in uses, so a durability computed from a damage reads back as that same damage. */
    private static final double EPSILON = 1e-9;

    private DurabilityScale() {}

    /** The points left after {@code damage} uses of an item of {@code max} points, at least 0; unbreakable: max. */
    public static double durability(int damage, int uses, double max) {
        if (max <= 0) {
            return max;
        }
        return Math.max(0, max - damage * (max / usesOf(uses, max)));
    }

    /**
     * The uses worn off a stack at {@code durability} points, rounded up and at most its uses; 0 when the stack is
     * unbreakable ({@code stackMax} of 0 or less). Measured against the item's own max, {@code itemMax} (the stack's
     * when the item has none): a Hytale repair lowers the stack's max, and those lost points count as damage, or the
     * stack would come back whole.
     */
    public static int damage(double durability, double stackMax, double itemMax, int uses) {
        if (stackMax <= 0) {
            return 0;
        }
        double max = itemMax > 0 ? itemMax : stackMax;
        int all = usesOf(uses, max);
        double worn = (max - durability) / (max / all);
        return (int) Math.min(all, Math.max(0, Math.ceil(worn - EPSILON)));
    }

    /** {@code uses}, or one per point of {@code max} for an item the core does not wear. */
    private static int usesOf(int uses, double max) {
        return uses > 0 ? uses : Math.max(1, (int) Math.ceil(max));
    }
}
