package dev.hycolony.api;

/**
 * A point in a world, such as where a citizen's body stands; its feet are at {@code y}.
 *
 * @since 1.0
 */
public record Vec(double x, double y, double z) {}
