package dev.hydomum.core.connect;

/**
 * The Minecraft family of the block choosing its shape: a wooden fence (MC FenceBlock in WOODEN_FENCES, as DO's
 * fence), another fence (MC nether brick fence), a wall (MC WallBlock) or bars (MC IronBarsBlock).
 */
public enum Joiner {
    WOODEN_FENCE,
    FENCE,
    WALL,
    PANE;

    /** What a block of this family is to its neighbours. */
    public NeighbourKind asNeighbour() {
        return switch (this) {
            case WOODEN_FENCE -> NeighbourKind.WOODEN_FENCE;
            case FENCE -> NeighbourKind.FENCE;
            case WALL -> NeighbourKind.WALL;
            case PANE -> NeighbourKind.PANE;
        };
    }
}
