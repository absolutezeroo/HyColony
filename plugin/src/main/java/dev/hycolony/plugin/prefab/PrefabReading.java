package dev.hycolony.plugin.prefab;

import com.hypixel.hytale.component.Holder;
import com.hypixel.hytale.server.core.prefab.PrefabRotation;
import com.hypixel.hytale.server.core.prefab.selection.buffer.PrefabBufferCall;
import com.hypixel.hytale.server.core.prefab.selection.buffer.impl.IPrefabBuffer;
import com.hypixel.hytale.server.core.universe.world.storage.ChunkStore;
import dev.hycolony.core.construction.blueprint.Blueprint;
import dev.hycolony.core.construction.blueprint.BlueprintEntry;
import dev.hycolony.core.construction.blueprint.BlueprintMarkers;
import dev.hycolony.core.kernel.BlockPos;
import dev.hycolony.core.kernel.item.BlockState;
import dev.hycolony.core.kernel.item.Workstation;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Random;
import org.jspecify.annotations.Nullable;

/**
 * One prefab buffer turned into a blueprint, by the rules {@link HytaleBlueprintSource} documents: pass 1 reads the
 * rotated, anchor-relative cells, pass 2 finds the hut cell and makes everything relative to it.
 */
final class PrefabReading {
    /** Hut-relative y of the floor the hut stands on: nothing below it is part of a Hytale prefab's blueprint. */
    private static final int FLOOR_Y = -1;

    /**
     * What cells turn into: {@code chest} replaces a chest spawner (null: none), {@code placeholders} marks a
     * MineColonies level (null: a Hytale prefab), whose fluid cells get {@code fluid}.
     */
    record Rules(@Nullable String chest, PrefabCells.@Nullable Placeholders placeholders, String fluid) {}

    /** A rotated, anchor-relative prefab cell. */
    private record Cell(int x, int y, int z, BlockState state, boolean container, Optional<Workstation> workstation) {}

    /** Pass 1's cells and placeholder cells, and the lowest y of the cells. */
    private record Cells(List<Cell> blocks, List<PrefabMarkers.Cell> marks, int lowestY) {}

    private PrefabReading() {}

    /** The blueprint of {@code buf} turned by {@code r}; empty when the prefab has no cell to build. */
    static Optional<Blueprint> blueprint(IPrefabBuffer buf, PrefabRotation r, PrefabStyles.Level entry, Rules rules) {
        Cells cells = readCells(buf, r, rules);
        if (cells.blocks().isEmpty() && cells.marks().isEmpty()) {
            return Optional.empty();
        }
        boolean mc = rules.placeholders() != null;
        // The unrotated hut cell, turned like the entries: a MineColonies level's default is the anchor.
        int[] hutCell = mc && entry.hutOffset() == null
                ? new int[3]
                : PrefabStyles.hutCell(entry.hutOffset(), buf, cells.lowestY());
        BlockPos hut = PrefabStyles.rotate(r, hutCell);
        List<BlueprintEntry> entries = hutRelative(cells.blocks(), hut, mc ? Integer.MIN_VALUE : FLOOR_Y);
        BlockPos low = PrefabStyles.relative(buf.getMinX(r), buf.getMinY(), buf.getMinZ(r), hut);
        BlockPos min = new BlockPos(low.x(), mc ? low.y() : Math.max(low.y(), FLOOR_Y + 1), low.z());
        BlockPos max = PrefabStyles.relative(buf.getMaxX(r), buf.getMaxY(), buf.getMaxZ(r), hut);
        Optional<BlueprintMarkers> markers =
                mc ? Optional.of(PrefabMarkers.of(cells.marks(), hut, rules.fluid())) : Optional.empty();
        return Optional.of(new Blueprint(entry.prefab(), List.copyOf(entries), min, max, markers));
    }

    /** The prefab's non-filler cells rotated by {@code r}, split into blocks and (for a MC level) placeholders. */
    private static Cells readCells(IPrefabBuffer buf, PrefabRotation r, Rules rules) {
        List<Cell> blocks = new ArrayList<>();
        List<PrefabMarkers.Cell> marks = new ArrayList<>();
        int[] lowestY = {Integer.MAX_VALUE};
        buf.forEach(
                IPrefabBuffer.iterateAllColumns(),
                (int x,
                        int y,
                        int z,
                        int blockId,
                        Holder<ChunkStore> holder,
                        int support,
                        int rotation,
                        int filler,
                        PrefabBufferCall call,
                        int fluidId,
                        int fluidLevel) -> {
                    if (filler != 0) {
                        return;
                    }
                    PrefabCells.Placeholders placeholders = rules.placeholders();
                    Optional<PrefabCells.Marker> marker = placeholders == null
                            ? Optional.empty()
                            : PrefabCells.marker(blockId, fluidId, placeholders);
                    if (marker.isPresent()) {
                        marks.add(new PrefabMarkers.Cell(x, y, z, marker.get()));
                        return;
                    }
                    PrefabCells.resolve(blockId, holder, rotation, fluidId, rules.chest())
                            .ifPresent(c -> {
                                lowestY[0] = Math.min(lowestY[0], y);
                                blocks.add(new Cell(x, y, z, c.state(), c.container(), c.workstation()));
                            });
                },
                null,
                null,
                new PrefabBufferCall(new Random(0), r));
        return new Cells(blocks, marks, lowestY[0]);
    }

    /** The cells as entries relative to {@code hut}, without the hut cell itself nor anything below {@code floorY}. */
    private static List<BlueprintEntry> hutRelative(List<Cell> cells, BlockPos hut, int floorY) {
        List<BlueprintEntry> entries = new ArrayList<>(cells.size());
        for (Cell c : cells) {
            BlockPos offset = PrefabStyles.relative(c.x(), c.y(), c.z(), hut);
            if (offset.y() >= floorY && (offset.x() != 0 || offset.y() != 0 || offset.z() != 0)) {
                entries.add(new BlueprintEntry(offset, c.state(), c.container(), c.workstation()));
            }
        }
        return entries;
    }
}
