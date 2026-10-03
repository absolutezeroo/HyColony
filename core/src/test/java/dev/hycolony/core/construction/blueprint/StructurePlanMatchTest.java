package dev.hycolony.core.construction.blueprint;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.hycolony.core.kernel.BlockPos;
import dev.hycolony.core.kernel.item.BlockKey;
import dev.hycolony.core.kernel.item.BlockKind;
import dev.hycolony.core.kernel.item.BlockState;
import dev.hycolony.core.testing.FakeCatalog;
import dev.hycolony.core.testing.FakeWorldBlocks;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

/**
 * What the world may hold for a cell to be done besides its exact block (Structurize and MC placement handlers'
 * doesWorldStateMatchBlueprintState); the Hytale blocks are those of docs/research/domaine1-suite.md.
 */
class StructurePlanMatchTest {
    private static final BlockPos HUT = new BlockPos(10, 64, 10);
    private static final BlockKey GRASS = new BlockKey("Soil_Grass");
    private static final BlockKey MUD = new BlockKey("Soil_Mud");
    private static final BlockKey DIRT = new BlockKey("Soil_Dirt");
    private static final BlockKey COARSE_DIRT = new BlockKey("Soil_Dirt_Dry");
    private static final BlockKey TILLED = new BlockKey("Soil_Dirt_Tilled");
    private static final BlockKey FENCE_END = new BlockKey("*HyDomum_Fence__Oak_State_Definitions_End");
    private static final BlockKey FENCE_POST = new BlockKey("*HyDomum_Fence__Oak_State_Definitions_Post");
    private static final BlockKey BIRCH_FENCE = new BlockKey("*HyDomum_Fence__Birch_State_Definitions_Post");
    private static final BlockKey STAIRS = new BlockKey("Rock_Stone_Cobble_Stairs");
    private static final BlockKey STAIRS_CORNER =
            new BlockKey("*Rock_Stone_Cobble_Stairs_State_Definitions_Corner_Left");
    private static final BlockKey STONE = new BlockKey("Rock_Stone");
    private static final BlockState WATER_SOURCE = new BlockState(new BlockKey("~fluid:Water_Source"), 0);
    private static final BlockKey FLOWING_WATER = new BlockKey("~fluid:Water");

    private static final BlockKey FERN = new BlockKey("Plant_Fern");

    private final FakeCatalog catalog = new FakeCatalog();
    private final FakeWorldBlocks blocks = new FakeWorldBlocks();

    StructurePlanMatchTest() {
        catalog.dirt.addAll(List.of(GRASS, MUD, DIRT, COARSE_DIRT));
        catalog.takesAnyDirt.addAll(List.of(GRASS, DIRT));
        catalog.shapeFamilies.put(FENCE_END, "HyDomum_Fence__Oak");
        catalog.shapeFamilies.put(FENCE_POST, "HyDomum_Fence__Oak");
        catalog.shapeFamilies.put(BIRCH_FENCE, "HyDomum_Fence__Birch");
        catalog.kinds.put(WATER_SOURCE.key(), BlockKind.FLUID);
        catalog.kinds.put(FLOWING_WATER, BlockKind.FLUID);
        catalog.flowing.add(FLOWING_WATER);
    }

    private boolean done(BlockKey planned, BlockKey world) {
        BlueprintEntry e = new BlueprintEntry(new BlockPos(0, 0, 0), new BlockState(planned, 0), false);
        StructurePlan plan = StructurePlan.build(
                new Blueprint("k", List.of(e), new BlockPos(0, 0, 0), new BlockPos(0, 0, 0)), HUT, catalog);
        return plan.satisfied(e, new BlockState(world, 0), blocks, catalog);
    }

    /** Structurize GrassPlacementHandler: a grass or dirt cell takes any block of MC's dirt tag. */
    @Test
    void grassCellIsDoneOnAnyDirtTagBlock() {
        assertTrue(done(GRASS, MUD));
        assertTrue(done(DIRT, GRASS));
    }

    /** MC's dirt tag has no farmland. */
    @Test
    void grassCellIsNotDoneOnTilledSoil() {
        assertFalse(done(GRASS, TILLED));
    }

    /** GrassPlacementHandler.canHandle takes grass and dirt only: coarse dirt keeps its own block. */
    @Test
    void coarseDirtCellStillWantsItsOwnBlock() {
        assertFalse(done(COARSE_DIRT, DIRT));
    }

    /** MC GeneralBlockPlacementHandler: a wall, fence, bars or gate of another shape is the same block. */
    @Test
    void fenceCellIsDoneByAnotherShapeOfItsFamily() {
        assertTrue(done(FENCE_END, FENCE_POST));
    }

    @Test
    void fenceCellIsNotDoneByAnotherFence() {
        assertFalse(done(FENCE_END, BIRCH_FENCE));
    }

    /** Stairs are no free-shape block for MC's GeneralBlockPlacementHandler: their shape must match. */
    @Test
    void stairsCellWantsItsShape() {
        assertFalse(done(STAIRS, STAIRS_CORNER));
    }

    private boolean fluidCellDone(BlockKey world) {
        catalog.kinds.put(STONE, BlockKind.SOLID);
        BlueprintMarkers markers = new BlueprintMarkers(
                List.of(), List.of(), List.of(new BlueprintEntry(new BlockPos(0, 0, 0), WATER_SOURCE, false)));
        Blueprint bp =
                new Blueprint("k", List.of(), new BlockPos(0, 0, 0), new BlockPos(0, 0, 0), Optional.of(markers));
        StructurePlan plan = StructurePlan.build(bp, HUT, catalog, STONE);
        BlueprintEntry cell = plan.decoList().getFirst();
        return plan.satisfied(cell, new BlockState(world, 0), blocks, catalog);
    }

    /** Structurize FluidSubstitutionPlacementHandler: a fluid cell takes a source or a solid block. */
    @Test
    void fluidCellIsDoneByASourceOrASolidBlock() {
        assertTrue(fluidCellDone(WATER_SOURCE.key()));
        assertTrue(fluidCellDone(STONE));
    }

    @Test
    void fluidCellIsNotDoneByFlowingWater() {
        assertFalse(fluidCellDone(FLOWING_WATER));
    }

    /** MC's waterlogged block → Hytale's block sharing its cell with a fluid source (FluidSection). */
    @Test
    void fluidCellIsDoneByABlockStandingInASource() {
        catalog.kinds.put(FERN, BlockKind.NON_SOLID);
        blocks.fluids.put(HUT, WATER_SOURCE);

        assertTrue(fluidCellDone(FERN));
    }

    @Test
    void fluidCellIsNotDoneByADryPlant() {
        catalog.kinds.put(FERN, BlockKind.NON_SOLID);

        assertFalse(fluidCellDone(FERN));
    }
}
