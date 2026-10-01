package dev.hydomum.core.connect;

/**
 * One side's neighbour, as the plugin read it: its kind, whether its face toward us is full (MC
 * BlockState.isFaceSturdy), and its yaw (0 to 3 quarter turns, as {@link Side} turns), which places a gate's sides.
 */
public record Neighbour(NeighbourKind kind, boolean fullFace, int yaw) {}
