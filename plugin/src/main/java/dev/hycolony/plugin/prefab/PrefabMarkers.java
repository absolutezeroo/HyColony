package dev.hycolony.plugin.prefab;

import dev.hycolony.core.construction.blueprint.BlueprintEntry;
import dev.hycolony.core.construction.blueprint.BlueprintMarkers;
import dev.hycolony.core.kernel.BlockPos;
import dev.hycolony.core.kernel.item.BlockKey;
import dev.hycolony.core.kernel.item.BlockState;
import dev.hycolony.plugin.block.HytaleBlockStates;
import java.util.ArrayList;
import java.util.List;

/** The placeholder cells of a MineColonies prefab turned into the core's {@link BlueprintMarkers}. */
final class PrefabMarkers {
    /** A rotated, anchor-relative placeholder cell. */
    record Cell(int x, int y, int z, PrefabCells.Marker marker) {}

    private PrefabMarkers() {}

    /** The cells relative to {@code hut}, the hut cell left out; fluid cells get {@code fluid} (a fluid asset id). */
    static BlueprintMarkers of(List<Cell> cells, BlockPos hut, String fluid) {
        BlockState fluidState = new BlockState(new BlockKey(HytaleBlockStates.FLUID_PREFIX + fluid), 0);
        List<BlockPos> air = new ArrayList<>();
        List<BlockPos> fill = new ArrayList<>();
        List<BlueprintEntry> fluids = new ArrayList<>();
        for (Cell c : cells) {
            BlockPos offset = PrefabStyles.relative(c.x(), c.y(), c.z(), hut);
            if (offset.x() == 0 && offset.y() == 0 && offset.z() == 0) {
                continue;
            }
            switch (c.marker()) {
                case AIR -> air.add(offset);
                case FILL -> fill.add(offset);
                case FLUID -> fluids.add(new BlueprintEntry(offset, fluidState, false));
            }
        }
        return new BlueprintMarkers(air, fill, fluids);
    }
}
