package dev.hydomum.core.connect;

/** What a neighbour is, for MC's connectsTo: one of the families of {@link Joiner}, a fence gate, or another block. */
public enum NeighbourKind {
    WOODEN_FENCE,
    FENCE,
    WALL,
    PANE,
    /** A fence gate (MC FenceGateBlock), joined from its sides only. */
    GATE,
    /** Any other block, joined by its full face only. */
    OTHER
}
