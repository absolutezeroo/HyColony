package dev.hycolony.core.construction.tape;

import dev.hycolony.core.kernel.BlockPos;
import dev.hycolony.core.kernel.catalog.BlockCatalog;
import dev.hycolony.core.kernel.item.BlockKind;
import dev.hycolony.core.kernel.item.BlockState;
import dev.hycolony.core.kernel.port.WorldBlocks;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * The tapes around a footprint (MC ConstructionTapeHelper.placeConstructionTape): its corners widened by one block,
 * each border cell on the first ground going down from the top (firstValidPosition), in MC's order, each cell once.
 *
 * <p>Deviation from MC: MC lets each tape take its shape from the tapes already around it (getPlacementState); here
 * each gets at once the shape MC's reaches when the border is done: straight along an edge, a corner at each angle.
 * A cell is free as Hytale's own placement sees it (empty, or a block of material Empty: NON_SOLID), where MC asks
 * canBeReplaced.
 */
final class TapeLayout {
    /** MC firstValidPosition: how much deeper than the footprint's height a tape looks for ground, in blocks. */
    private static final int EXTRA_DEPTH = 5;
    /**
     * The corner's rotation at the north-west, north-east, south-west and south-east angles: the one joining the two
     * edges that meet there (the corner joins north and east at rotation 0, each quarter turn takes north to west).
     */
    private static final int[] CORNER_ROTATIONS = {3, 2, 0, 1};

    private TapeLayout() {}

    /** The tapes around the footprint from {@code min} to {@code max}; a column with no ground in reach has none. */
    static List<Tape> of(BlockPos min, BlockPos max, WorldBlocks world, BlockCatalog catalog) {
        Border border = Border.around(min, max);
        int top = Math.max(min.y(), max.y());
        int depth = Math.abs(max.y() - min.y()) + EXTRA_DEPTH;
        Map<BlockPos, Tape> tapes = new LinkedHashMap<>();
        for (BlockPos column : border.columns()) {
            if (!tapes.containsKey(column)) {
                ground(column, top, depth, world, catalog).ifPresent(at -> tapes.put(column, border.tapeAt(at)));
            }
        }
        return new ArrayList<>(tapes.values());
    }

    /**
     * MC removeConstructionTape's walk of the border around the footprint from {@code min} to {@code max}, at y 0:
     * the north and south edges, the west and east edges, then the four corners, so that a corner comes three times.
     */
    static List<BlockPos> removalColumns(BlockPos min, BlockPos max) {
        Border b = Border.around(min, max);
        int x3 = b.x() + b.sizeX();
        int z3 = b.z() + b.sizeZ();
        List<BlockPos> out = new ArrayList<>();
        for (int x = b.x(); x <= x3; x++) {
            out.add(new BlockPos(x, 0, b.z()));
            out.add(new BlockPos(x, 0, z3));
        }
        for (int z = b.z(); z <= z3; z++) {
            out.add(new BlockPos(b.x(), 0, z));
            out.add(new BlockPos(x3, 0, z));
        }
        out.addAll(List.of(
                new BlockPos(b.x(), 0, b.z()), new BlockPos(b.x(), 0, z3),
                new BlockPos(x3, 0, b.z()), new BlockPos(x3, 0, z3)));
        return out;
    }

    /** MC firstValidPosition: above the first solid block, going down from {@code top}, whose upper cell is free. */
    private static Optional<BlockPos> ground(BlockPos column, int top, int depth, WorldBlocks world, BlockCatalog c) {
        for (int i = 0; i <= depth; i++) {
            BlockPos at = new BlockPos(column.x(), top - i, column.z());
            BlockPos up = at.offset(0, 1, 0);
            if (solid(world.get(at), c) && free(world.get(up), c)) {
                return Optional.of(up);
            }
        }
        return Optional.empty();
    }

    private static boolean solid(Optional<BlockState> s, BlockCatalog catalog) {
        return s.map(st -> catalog.kind(st.key()))
                .filter(k -> k == BlockKind.SOLID || k == BlockKind.UNBREAKABLE)
                .isPresent();
    }

    /** MC canBeReplaced or isAir: empty, a block of Hytale's material Empty, or a fluid (MC waterlogs the tape). */
    private static boolean free(Optional<BlockState> s, BlockCatalog catalog) {
        return s.map(st -> catalog.kind(st.key()))
                .map(k -> k == BlockKind.AIR || k == BlockKind.NON_SOLID || k == BlockKind.FLUID)
                .orElse(true);
    }

    /** The widened border: its north-west corner ({@code x}, {@code z}) and its sizes, MC's sizeX and sizeZ. */
    private record Border(int x, int z, int sizeX, int sizeZ) {
        /** MC's corners widened by one block on X and Z. */
        static Border around(BlockPos min, BlockPos max) {
            return new Border(
                    Math.min(min.x(), max.x()) - 1,
                    Math.min(min.z(), max.z()) - 1,
                    Math.abs(max.x() - min.x()) + 2,
                    Math.abs(max.z() - min.z()) + 2);
        }

        /** MC's walk: north and south edges, then west and east, step by step, then the south-east corner. */
        List<BlockPos> columns() {
            List<BlockPos> out = new ArrayList<>();
            for (int step = 0; step < Math.max(sizeX, sizeZ); step++) {
                if (step < sizeX) {
                    out.add(new BlockPos(x + step, 0, z));
                    out.add(new BlockPos(x + step, 0, z + sizeZ));
                }
                if (step < sizeZ) {
                    out.add(new BlockPos(x, 0, z + step));
                    out.add(new BlockPos(x + sizeX, 0, z + step));
                }
            }
            out.add(new BlockPos(x + sizeX, 0, z + sizeZ));
            return out;
        }

        /** The tape at {@code at}: a corner joining its two edges at an angle, else straight along its edge. */
        Tape tapeAt(BlockPos at) {
            boolean onWestOrEast = at.x() == x || at.x() == x + sizeX;
            boolean onNorthOrSouth = at.z() == z || at.z() == z + sizeZ;
            if (onWestOrEast && onNorthOrSouth) {
                int angle = (at.z() == z ? 0 : 2) + (at.x() == x ? 0 : 1);
                return new Tape(at, TapeShape.CORNER, CORNER_ROTATIONS[angle]);
            }
            return new Tape(at, TapeShape.STRAIGHT, onNorthOrSouth ? 1 : 0);
        }
    }
}
