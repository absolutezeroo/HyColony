package dev.hycolony.core.farming.field;

import java.util.Optional;

/**
 * How far a field reaches from its field block on each side (MC FarmField.radii, TileEntityScarecrow.getFieldSize).
 * The four radii together never exceed {@link #MAX_RANGE}: a side only grows when another has shrunk.
 */
public record FieldRadii(int south, int west, int north, int east) {
    /** MC FarmField.MAX_RANGE: the largest radius, and the largest sum of the four. */
    public static final int MAX_RANGE = 20;

    /** MC FarmField.DEFAULT_RANGE. */
    public static final int DEFAULT_RANGE = 5;

    /** A side of the field; the order is MC's {@code Direction.get2DDataValue} (S, W, N, E). */
    public enum Direction {
        SOUTH,
        WEST,
        NORTH,
        EAST
    }

    /** 5 on every side. */
    public static FieldRadii defaults() {
        return new FieldRadii(DEFAULT_RANGE, DEFAULT_RANGE, DEFAULT_RANGE, DEFAULT_RANGE);
    }

    /** The radius of side {@code dir}. */
    public int get(Direction dir) {
        return switch (dir) {
            case SOUTH -> south;
            case WEST -> west;
            case NORTH -> north;
            case EAST -> east;
        };
    }

    /**
     * MC FarmFieldPlotResizeMessage: side {@code dir} set to {@code size}, capped at {@link #MAX_RANGE}; empty
     * (refused) when {@code size} is negative, or when it grows the side past the budget of the four radii.
     */
    public Optional<FieldRadii> resized(Direction dir, int size) {
        int current = get(dir);
        if (size < 0 || (size > current && sum() - current + size > MAX_RANGE)) {
            return Optional.empty();
        }
        return Optional.of(with(dir, Math.min(size, MAX_RANGE)));
    }

    /**
     * MC WindowField radius button: {@code (current % min(current + leftOver, 20)) + 1}, so the side cycles from 1 up
     * to what the budget allows, then back to 1. Deviation from MC: a zero side with nothing left over stays as it is,
     * where MC's client divides by zero.
     */
    public FieldRadii cycled(Direction dir) {
        int current = get(dir);
        int bound = Math.min(current + (MAX_RANGE - sum()), MAX_RANGE);
        return bound <= 0 ? this : with(dir, current % bound + 1);
    }

    private int sum() {
        return south + west + north + east;
    }

    private FieldRadii with(Direction dir, int size) {
        return switch (dir) {
            case SOUTH -> new FieldRadii(size, west, north, east);
            case WEST -> new FieldRadii(south, size, north, east);
            case NORTH -> new FieldRadii(south, west, size, east);
            case EAST -> new FieldRadii(south, west, north, size);
        };
    }
}
