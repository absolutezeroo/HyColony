package dev.hycolony.core.construction.blueprint;

import dev.hycolony.core.kernel.BlockPos;
import dev.hycolony.core.kernel.item.BlockKind;
import dev.hycolony.core.kernel.item.BlockState;
import dev.hycolony.core.kernel.port.ItemCatalog;
import dev.hycolony.core.kernel.port.WorldBlocks;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Precomputed, immutable work lists for one work order's plan. {@link #build} runs once per order;
 * the getters just hand back the already-sorted lists.
 */
public final class StructurePlan {
    private static final Comparator<BlueprintEntry> BOTTOM_UP = Comparator.comparingInt(
                    (BlueprintEntry e) -> e.offset().y())
            .thenComparingInt(e -> e.offset().x())
            .thenComparingInt(e -> e.offset().z());

    private static final Comparator<BlockPos> TOP_DOWN = Comparator.comparingInt(BlockPos::y)
            .reversed()
            .thenComparingInt(BlockPos::x)
            .thenComparingInt(BlockPos::z);

    private final BlockPos hut;
    private final List<BlockPos> clearList;
    private final List<BlueprintEntry> solidList;
    private final List<BlueprintEntry> decoList;
    private final List<BlockPos> removeList;
    private final List<BlockPos> solidPositions;
    private final List<BlockPos> decoPositions;
    private final Map<BlockPos, BlockState> stateAt;

    private StructurePlan(
            BlockPos hut,
            List<BlockPos> clearList,
            List<BlueprintEntry> solidList,
            List<BlueprintEntry> decoList,
            List<BlockPos> removeList,
            Map<BlockPos, BlockState> stateAt) {
        this.hut = hut;
        this.clearList = clearList;
        this.solidList = solidList;
        this.decoList = decoList;
        this.removeList = removeList;
        this.solidPositions = solidList.stream().map(this::worldPos).toList();
        this.decoPositions = decoList.stream().map(this::worldPos).toList();
        this.stateAt = stateAt;
    }

    public static StructurePlan build(Blueprint bp, BlockPos hut, ItemCatalog catalog) {
        List<BlockPos> clear = buildClearList(bp, hut);

        List<BlueprintEntry> solid = new ArrayList<>();
        List<BlueprintEntry> deco = new ArrayList<>();
        List<BlockPos> remove = new ArrayList<>();
        Map<BlockPos, BlockState> stateAt = new HashMap<>();
        for (BlueprintEntry e : bp.entries()) {
            stateAt.put(hut.offset(e.offset().x(), e.offset().y(), e.offset().z()), e.state());
            BlockKind kind = catalog.kind(e.state().key());
            switch (kind) {
                case SOLID -> solid.add(e);
                case NON_SOLID, FLUID -> deco.add(e);
                case AIR, UNBREAKABLE -> {}
            }
            if (kind != BlockKind.AIR) {
                remove.add(hut.offset(e.offset().x(), e.offset().y(), e.offset().z()));
            }
        }
        solid.sort(BOTTOM_UP);
        deco.sort(BOTTOM_UP);
        remove.sort(TOP_DOWN);
        return new StructurePlan(hut, clear, List.copyOf(solid), List.copyOf(deco), List.copyOf(remove), stateAt);
    }

    /** Every position in [hut+min, hut+max], y desc then x asc then z asc, excluding the hut itself. */
    private static List<BlockPos> buildClearList(Blueprint bp, BlockPos hut) {
        BlockPos min = bp.min();
        BlockPos max = bp.max();
        int volume = (max.x() - min.x() + 1) * (max.y() - min.y() + 1) * (max.z() - min.z() + 1);
        List<BlockPos> list = new ArrayList<>(Math.max(0, volume));
        for (int y = max.y(); y >= min.y(); y--) {
            for (int x = min.x(); x <= max.x(); x++) {
                for (int z = min.z(); z <= max.z(); z++) {
                    if (x == 0 && y == 0 && z == 0) {
                        continue; // the hut block itself is always at offset (0,0,0)
                    }
                    list.add(hut.offset(x, y, z));
                }
            }
        }
        return List.copyOf(list);
    }

    public List<BlockPos> clearList() {
        return clearList;
    }

    public List<BlueprintEntry> solidList() {
        return solidList;
    }

    public List<BlueprintEntry> decoList() {
        return decoList;
    }

    public List<BlockPos> removeList() {
        return removeList;
    }

    /** World positions of {@link #solidList()}, same order (precomputed: the builder scans them every step). */
    public List<BlockPos> solidPositions() {
        return solidPositions;
    }

    /** World positions of {@link #decoList()}, same order. */
    public List<BlockPos> decoPositions() {
        return decoPositions;
    }

    /** The planned state at a world position, or null where the plan has nothing (air). */
    public BlockState stateAt(BlockPos worldPos) {
        return stateAt.get(worldPos);
    }

    public BlockPos worldPos(BlueprintEntry e) {
        return hut.offset(e.offset().x(), e.offset().y(), e.offset().z());
    }

    /** True when the world already has this entry's exact state (key and rotation) at its position. */
    public boolean isDone(BlueprintEntry e, WorldBlocks world) {
        return world.get(worldPos(e)).map(state -> state.equals(e.state())).orElse(false);
    }
}
