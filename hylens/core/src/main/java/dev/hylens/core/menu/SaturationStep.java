package dev.hylens.core.menu;

/**
 * The menu's saturation buttons (spec 2026-10-02 lot 2, § 5), MC's suggestions for /mc citizens modify saturation: "="
 * 0 or the maximum, "+" and "-" 1.
 */
public enum SaturationStep {
    ZERO,
    LESS,
    MORE,
    MAX;

    /** What "+" and "-" add or take (MC's suggestion). */
    static final double STEP = 1.0;

    /** The saturation this button asks, from {@code current}, kept between 0 and {@code max}. */
    public double from(double current, double max) {
        double asked = switch (this) {
            case ZERO -> 0;
            case LESS -> current - STEP;
            case MORE -> current + STEP;
            case MAX -> max;
        };
        return Math.clamp(asked, 0, max);
    }
}
