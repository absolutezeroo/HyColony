package dev.hycolony.core.construction.blueprint;

import dev.hycolony.core.kernel.BlockPos;
import dev.hycolony.core.kernel.item.BlockKey;
import dev.hycolony.core.kernel.item.BlockKind;
import dev.hycolony.core.kernel.item.BlockState;
import dev.hycolony.core.kernel.port.ItemCatalog;
import dev.hycolony.core.kernel.port.WorldBlocks;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.jspecify.annotations.Nullable;

/**
 * Precomputed, immutable work lists for one work order's plan. {@link #build} runs once per order;
 * the getters just hand back the already-sorted lists. A blueprint with markers (MineColonies) also has fill and
 * fluid cells, which the scan judges by the world rather than by equality (MC Solid/FluidSubstitutionPlacementHandler).
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
    private final Cells cells;

    /** The sorted SOLID, DECORATE and REMOVE lists, and the planned state by world position. */
    private record Lists(
            List<BlueprintEntry> solid,
            List<BlueprintEntry> deco,
            List<BlockPos> remove,
            Map<BlockPos, BlockState> stateAt) {}

    /**
     * A MineColonies plan's placeholder world positions: fill and fluid cells, and the air cells top-down; all empty
     * for a plan without markers ({@code marked} false).
     */
    private record Cells(boolean marked, Set<BlockPos> fill, Set<BlockPos> fluid, List<BlockPos> air) {}

    private StructurePlan(BlockPos hut, List<BlockPos> clearList, Lists lists, Cells cells) {
        this.hut = hut;
        this.clearList = clearList;
        this.solidList = lists.solid();
        this.decoList = lists.deco();
        this.removeList = lists.remove();
        this.solidPositions = solidList.stream().map(this::worldPos).toList();
        this.decoPositions = decoList.stream().map(this::worldPos).toList();
        this.stateAt = lists.stateAt();
        this.cells = cells;
    }

    /**
     * The plan of {@code bp} at {@code hut}, whose fill cells, if any, get no block: for Hytale prefabs (no markers)
     * and for an UPGRADE's previous level, of which only the remove list is used.
     */
    public static StructurePlan build(Blueprint bp, BlockPos hut, ItemCatalog catalog) {
        return build(bp, hut, catalog, Optional.empty());
    }

    /** The plan of {@code bp} at {@code hut}; its fill cells get {@code fillBlock} (MC BUILDER_SETTINGS fillblock). */
    public static StructurePlan build(Blueprint bp, BlockPos hut, ItemCatalog catalog, BlockKey fillBlock) {
        return build(bp, hut, catalog, Optional.of(fillBlock));
    }

    private static StructurePlan build(Blueprint bp, BlockPos hut, ItemCatalog catalog, Optional<BlockKey> fillBlock) {
        List<BlueprintEntry> planned = new ArrayList<>(bp.entries());
        Set<BlockPos> fills = new HashSet<>();
        Set<BlockPos> fluids = new HashSet<>();
        bp.markers().ifPresent(m -> {
            fillBlock.ifPresent(block -> m.fill().forEach(offset -> {
                planned.add(new BlueprintEntry(offset, new BlockState(block, 0), false));
                fills.add(hut.offset(offset.x(), offset.y(), offset.z()));
            }));
            planned.addAll(m.fluid());
            m.fluid()
                    .forEach(e -> fluids.add(hut.offset(
                            e.offset().x(), e.offset().y(), e.offset().z())));
        });
        List<BlockPos> clear = bp.markers().isPresent() ? markedClearList(bp, hut, fills) : buildClearList(bp, hut);
        List<BlockPos> air = bp.markers()
                .map(m -> m.air().stream()
                        .map(o -> hut.offset(o.x(), o.y(), o.z()))
                        .sorted(TOP_DOWN)
                        .toList())
                .orElse(List.of());
        Set<BlockPos> terrain = new HashSet<>(fills);
        terrain.addAll(fluids);
        return new StructurePlan(
                hut,
                clear,
                sortedLists(planned, hut, catalog, terrain),
                new Cells(bp.markers().isPresent(), Set.copyOf(fills), Set.copyOf(fluids), air));
    }

    /**
     * Sorts {@code planned} into the stage lists; {@code terrain} (fill and fluid cells) is never removed with the
     * building (MC AbstractEntityAIStructure.skipRemoval skips the substitution blocks).
     */
    private static Lists sortedLists(
            List<BlueprintEntry> planned, BlockPos hut, ItemCatalog catalog, Set<BlockPos> terrain) {
        List<BlueprintEntry> solid = new ArrayList<>();
        List<BlueprintEntry> deco = new ArrayList<>();
        List<BlockPos> remove = new ArrayList<>();
        Map<BlockPos, BlockState> stateAt = new HashMap<>();
        for (BlueprintEntry e : planned) {
            BlockPos pos = hut.offset(e.offset().x(), e.offset().y(), e.offset().z());
            stateAt.put(pos, e.state());
            BlockKind kind = catalog.kind(e.state().key());
            switch (kind) {
                case SOLID -> solid.add(e);
                case NON_SOLID, FLUID -> deco.add(e);
                case AIR, UNBREAKABLE -> {}
            }
            if (kind != BlockKind.AIR && !terrain.contains(pos)) {
                remove.add(pos);
            }
        }
        solid.sort(BOTTOM_UP);
        deco.sort(BOTTOM_UP);
        remove.sort(TOP_DOWN);
        return new Lists(List.copyOf(solid), List.copyOf(deco), List.copyOf(remove), stateAt);
    }

    /**
     * A MineColonies blueprint's CLEAR positions, top-down: its air, blocks and fill cells, without the hut. Absent
     * (substitution) and fluid cells are never cleared (MC AbstractEntityAIStructure.skipClearing; the iterator skips
     * cells that already match, which a substitution always does).
     */
    private static List<BlockPos> markedClearList(Blueprint bp, BlockPos hut, Set<BlockPos> fills) {
        Set<BlockPos> cells = new HashSet<>(fills);
        bp.entries()
                .forEach(e -> cells.add(
                        hut.offset(e.offset().x(), e.offset().y(), e.offset().z())));
        bp.markers().orElseThrow().air().forEach(o -> cells.add(hut.offset(o.x(), o.y(), o.z())));
        cells.remove(hut);
        return cells.stream().sorted(TOP_DOWN).toList();
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

    /** The hut block's world position, the plan's anchor. */
    public BlockPos hut() {
        return hut;
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
    public @Nullable BlockState stateAt(BlockPos worldPos) {
        return stateAt.get(worldPos);
    }

    /** Whether the plan fills this world position with the fill block (a blocksolidsubstitution cell). */
    public boolean isFillCell(BlockPos worldPos) {
        return cells.fill().contains(worldPos);
    }

    /** Whether the plan comes from a MineColonies blueprint (it has markers). */
    public boolean hasMarkers() {
        return cells.marked();
    }

    /** The plan's explicit air cells, top-down; empty without markers. */
    public List<BlockPos> airPositions() {
        return cells.air();
    }

    public BlockPos worldPos(BlueprintEntry e) {
        return hut.offset(e.offset().x(), e.offset().y(), e.offset().z());
    }

    /** True when the world already has what this entry asks for at its position (see {@link #satisfied}). */
    public boolean isDone(BlueprintEntry e, WorldBlocks world, ItemCatalog catalog) {
        return satisfied(e, world.get(worldPos(e)).orElse(null), catalog);
    }

    /**
     * Whether {@code world} (null: nothing) already answers the entry: its exact state (key and rotation); for a fill
     * cell any good floor (MC SolidSubstitutionPlacementHandler); for a fluid cell any solid block or any fluid (MC
     * FluidSubstitutionPlacementHandler: isAnySolid or a fluid source).
     *
     * <p>Deviation from MC: any fluid counts, as the core cannot tell a source from a flowing fluid.
     */
    public boolean satisfied(BlueprintEntry e, @Nullable BlockState world, ItemCatalog catalog) {
        if (world == null) {
            return false;
        }
        if (world.equals(e.state())) {
            return true;
        }
        if (!cells.marked()) {
            return false; // no allocation for Hytale prefabs, scanned every step
        }
        BlockPos pos = worldPos(e);
        if (cells.fill().contains(pos)) {
            return catalog.isGoodFloor(world.key());
        }
        BlockKind kind = catalog.kind(world.key());
        return cells.fluid().contains(pos) && (kind == BlockKind.SOLID || kind == BlockKind.FLUID);
    }
}
