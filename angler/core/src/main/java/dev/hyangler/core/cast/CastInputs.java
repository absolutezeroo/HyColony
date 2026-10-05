package dev.hyangler.core.cast;

/**
 * What the plugin reads of a bobber each core tick: in water, on the ground, its line's length, rain and sky on it,
 * and whether open water surrounds it (OpenWater, read only while a fish approaches or bites: the plugin may skip the
 * scan and pass true otherwise). Built at the call to {@link CastSession#tick} and never kept, so the JIT's escape
 * analysis can drop it: one small record per bobber and tick at most, a handful a tick on a server.
 */
public record CastInputs(
        boolean inWater, boolean onGround, double distance, boolean raining, boolean skyVisible, boolean openWater) {}
