package dev.hycolony.core.kernel.item;

/**
 * Hytale's gather powers in the core's terms: a block's hardness from its gather type's bare-hand power, and a tool's
 * level, speed and uses from its spec for that gather type. Deviation from MC: MC reads a block's hardness and a
 * tool's tier and durability directly; Hytale only gives gather powers, qualities and durability losses, so the core
 * derives them here.
 */
public final class ToolScale {
    /** The hardness of a block bare hands break in one hit, or without a gather type. */
    public static final float MIN_HARDNESS = 0.05f;

    /** The hardest block. */
    public static final float MAX_HARDNESS = 3f; // ponytail: heuristic cap, replace by a table if balance is off

    private ToolScale() {}

    /**
     * A block's hardness from its gather type's bare-hand power: {@link #MIN_HARDNESS} divided by it, within
     * [{@link #MIN_HARDNESS}, {@link #MAX_HARDNESS}]; 1 when bare hands cannot break it (power 0 or less).
     */
    public static float hardness(float unarmedPower) {
        return unarmedPower <= 0 ? 1f : Math.clamp(MIN_HARDNESS / unarmedPower, MIN_HARDNESS, MAX_HARDNESS);
    }

    /** A tool's level: its spec's quality above wood's 1, at least 0. */
    public static int level(int quality) {
        return Math.max(0, quality - 1);
    }

    /** A tool's speed on its gather type: its power over bare hands' (its own when theirs is 0); 1 without power. */
    public static float speed(float power, float unarmedPower) {
        if (power <= 0) {
            return 1f;
        }
        return unarmedPower <= 0 ? power : power / unarmedPower;
    }

    /**
     * Blocks of its own gather type a tool mines before it breaks: its max durability over the loss per hit times the
     * hits one block takes ({@code ceil(1 / power)}), at least 1; 0 for an unbreakable or unworn tool.
     */
    public static int uses(double maxDurability, double lossPerHit, float power) {
        if (maxDurability <= 0 || lossPerHit <= 0) {
            return 0;
        }
        double hitsPerBlock = power > 0 ? Math.ceil(1 / power) : 1;
        return Math.max(1, (int) (maxDurability / (lossPerHit * hitsPerBlock)));
    }
}
