package dev.hyangler.core.cast;

/** What the plugin reads of a bobber each core tick: in water, on the ground, its line's length, rain and sky on it. */
public record CastInputs(boolean inWater, boolean onGround, double distance, boolean raining, boolean skyVisible) {}
