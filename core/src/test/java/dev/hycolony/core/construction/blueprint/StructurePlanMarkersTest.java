package dev.hycolony.core.construction.blueprint;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.hycolony.core.kernel.BlockPos;
import dev.hycolony.core.kernel.item.BlockKey;
import dev.hycolony.core.kernel.item.BlockKind;
import dev.hycolony.core.kernel.item.BlockState;
import dev.hycolony.core.testing.FakeCatalog;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class StructurePlanMarkersTest {
    private static final BlockKey STONE = new BlockKey("stone");
    private static final BlockKey DIRT = new BlockKey("dirt");
    private static final BlockState WATER = new BlockState(new BlockKey("~fluid:Water_Source"), 0);
    private static final BlockPos HUT = new BlockPos(10, 64, 10);

    /** Stone at (0,1,0), air at (1,1,0), fill at (0,-1,0), fluid at (1,-1,0); the rest of the box is absent. */
    private static StructurePlan plan() {
        FakeCatalog catalog = new FakeCatalog();
        catalog.kinds.put(STONE, BlockKind.SOLID);
        catalog.kinds.put(DIRT, BlockKind.SOLID);
        catalog.kinds.put(WATER.key(), BlockKind.FLUID);
        BlueprintMarkers markers = new BlueprintMarkers(
                List.of(new BlockPos(1, 1, 0)),
                List.of(new BlockPos(0, -1, 0)),
                List.of(new BlueprintEntry(new BlockPos(1, -1, 0), WATER, false)));
        Blueprint bp = new Blueprint(
                "k",
                List.of(new BlueprintEntry(new BlockPos(0, 1, 0), new BlockState(STONE, 0), false)),
                new BlockPos(-1, -1, -1),
                new BlockPos(1, 1, 1),
                Optional.of(markers));
        return StructurePlan.build(bp, HUT, catalog, DIRT);
    }

    @Test
    void clearVisitsOnlyAirBlocksAndFillCellsTopDown() {
        assertEquals(List.of(HUT.offset(0, 1, 0), HUT.offset(1, 1, 0), HUT.offset(0, -1, 0)), plan().clearList());
    }

    @Test
    void fillCellsBecomeSolidEntriesOfTheFillBlock() {
        StructurePlan plan = plan();
        assertEquals(List.of(HUT.offset(0, -1, 0), HUT.offset(0, 1, 0)), plan.solidPositions());
        assertEquals(DIRT, plan.solidList().getFirst().state().key());
        assertTrue(plan.isFill(HUT.offset(0, -1, 0)));
        assertFalse(plan.isFill(HUT.offset(0, 1, 0)));
    }

    @Test
    void fluidCellsJoinDecorate() {
        StructurePlan plan = plan();
        assertEquals(List.of(HUT.offset(1, -1, 0)), plan.decoPositions());
        assertTrue(plan.isFluidFill(HUT.offset(1, -1, 0)));
    }

    @Test
    void absentCellsAreNeitherPlannedNorRemoved() {
        StructurePlan plan = plan();
        BlockPos absent = HUT.offset(-1, 0, -1);
        assertFalse(plan.clearList().contains(absent));
        assertFalse(plan.removeList().contains(absent));
    }
}
