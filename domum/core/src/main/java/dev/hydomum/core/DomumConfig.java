package dev.hydomum.core;

/**
 * HyDomum's settings, clamped as HyColony's ColonyConfig clamps its own.
 *
 * @param cutterCraftSeconds how long one craft at the architect's cutter takes, in seconds; 0 crafts at once
 *     (Deviation from MC: DO's cutter crafts at once, Hytale's benches take time, CraftingManager.queueCraft)
 */
public record DomumConfig(double cutterCraftSeconds) {
    private static final double MAX_CUTTER_CRAFT_SECONDS = 10;
    private static final double DEFAULT_CUTTER_CRAFT_SECONDS = 0.5;

    public DomumConfig {
        cutterCraftSeconds = Math.clamp(cutterCraftSeconds, 0, MAX_CUTTER_CRAFT_SECONDS);
    }

    /** The defaults: half a second per craft. */
    public static DomumConfig defaults() {
        return new DomumConfig(DEFAULT_CUTTER_CRAFT_SECONDS);
    }
}
