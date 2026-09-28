package dev.hycolony.core.farming.field;

import java.util.OptionalInt;

/**
 * The order the farmer walks a field in (MC EntityAIWorkFarmer.nextValidCell): an outward square spiral around the
 * field block, ring by ring, 8 × ring cells per ring, skipping cells outside the radii. x grows to the east, z to the
 * south.
 */
public final class FieldCells {
    /** MC getLargestCell: {@code (2 × MAX_RANGE + 1)²}, the index where the walk ends. */
    public static final int LARGEST_CELL = (2 * FieldRadii.MAX_RANGE + 1) * (2 * FieldRadii.MAX_RANGE + 1);

    private FieldCells() {}

    /** The first cell index after {@code cell} (-1 to start) whose offset lies in {@code radii}; empty at the end. */
    public static OptionalInt next(int cell, FieldRadii radii) {
        for (int c = cell + 1; c < LARGEST_CELL; c++) {
            int[] o = offset(c);
            if (-o[1] <= radii.north() && o[0] <= radii.east() && o[1] <= radii.south() && -o[0] <= radii.west()) {
                return OptionalInt.of(c);
            }
        }
        return OptionalInt.empty();
    }

    /** The {x, z} offset of cell index {@code cell}, MC's ring / ringCell / facing arithmetic kept as is. */
    public static int[] offset(int cell) {
        int ring = Math.max(1, (int) Math.floor((Math.sqrt(cell + 1D) + 1) / 2.0));
        int ringCell = cell - (int) (4 * Math.pow(ring - 1D, 2) + 4 * (ring - 1));
        int facing = Math.floorDiv(ringCell, 2 * ring); // MC Direction.from2DDataValue: 0 S, 1 W, 2 N, 3 E
        int along = ring - ringCell % (2 * ring);
        return switch (facing) {
            case 0 -> new int[] {along, ring};
            case 1 -> new int[] {-ring, along};
            case 2 -> new int[] {-along, -ring};
            default -> new int[] {ring, -along};
        };
    }
}
