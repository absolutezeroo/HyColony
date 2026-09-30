package dev.hylens.core.draw;

/**
 * Which drawings an operator wants (spec 2026-09-30, § 6.4): the walk target (line and sphere), the last walk's stop
 * cell, the workplace, and the red of a failed walk.
 */
public record Layers(boolean target, boolean stop, boolean zone, boolean alerts) {
    /** Every layer shown. */
    public static final Layers ALL = new Layers(true, true, true, true);

    /** One layer an operator can turn on or off. */
    public enum Layer {
        TARGET,
        STOP,
        ZONE,
        ALERTS
    }

    /** Whether {@code layer} is shown. */
    public boolean shows(Layer layer) {
        return switch (layer) {
            case TARGET -> target;
            case STOP -> stop;
            case ZONE -> zone;
            case ALERTS -> alerts;
        };
    }

    /** These layers with {@code layer} turned on if it was off, off if it was on. */
    public Layers toggle(Layer layer) {
        return new Layers(
                target ^ layer == Layer.TARGET,
                stop ^ layer == Layer.STOP,
                zone ^ layer == Layer.ZONE,
                alerts ^ layer == Layer.ALERTS);
    }
}
