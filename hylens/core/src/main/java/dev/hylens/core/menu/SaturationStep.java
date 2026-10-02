package dev.hylens.core.menu;

import dev.hycolony.api.debug.SaturationChange;

/**
 * The menu's saturation buttons (spec 2026-10-02 lot 2, § 5), MC's /mc citizens modify saturation operators with its
 * suggested values: "=" 0 or the maximum, "-" and "+" 1. HyColony then applies MC's rules (the food modifier on "-").
 */
public enum SaturationStep {
    ZERO(SaturationChange.SET),
    LESS(SaturationChange.DECREASE),
    MORE(SaturationChange.INCREASE),
    MAX(SaturationChange.SET);

    /** What "-" and "+" take or add (MC's suggestion). */
    static final double STEP = 1.0;

    private final SaturationChange change;

    SaturationStep(SaturationChange change) {
        this.change = change;
    }

    /** MC's operator this button runs. */
    public SaturationChange change() {
        return change;
    }

    /** The value sent with {@link #change}: 0, the maximum {@code max}, or 1 for "-" and "+". */
    public double value(double max) {
        return switch (this) {
            case ZERO -> 0;
            case MAX -> max;
            case LESS, MORE -> STEP;
        };
    }
}
