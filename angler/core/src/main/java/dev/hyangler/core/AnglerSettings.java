package dev.hyangler.core;

/**
 * HyAngler's own settings, from the HyAngler section of its config.json (spec § 9), brought back within their bounds
 * by the core, as HyColony's ColonyConfig does.
 *
 * @param biteTimeMultiplier scales vanilla's wait, from 0.1 to 10
 * @param rodWear whether a cast wears the rod
 * @param maxLineDefault a rod's line when its file gives none, from 8 to 64 blocks
 * @param testCommandOpOnly whether /hyangler test is for operators only
 */
public record AnglerSettings(
        double biteTimeMultiplier, boolean rodWear, int maxLineDefault, boolean testCommandOpOnly) {
    /** The defaults: vanilla's wait, wear, vanilla's 32 blocks, operators only. */
    public static final AnglerSettings DEFAULTS = new AnglerSettings(1.0, true, 32, true);

    /** These settings within their bounds; a multiplier that is not a number takes its default. */
    public AnglerSettings bounded() {
        double multiplier = Double.isNaN(biteTimeMultiplier) ? 1.0 : Math.clamp(biteTimeMultiplier, 0.1, 10.0);
        return new AnglerSettings(multiplier, rodWear, Math.clamp(maxLineDefault, 8, 64), testCommandOpOnly);
    }
}
