package dev.hycolony.core.farming.job;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.hycolony.core.farming.CropState;
import dev.hycolony.core.kernel.BlockPos;
import dev.hycolony.core.kernel.item.BlockKey;
import dev.hycolony.core.kernel.item.BlockKind;
import dev.hycolony.core.kernel.item.BlockState;
import dev.hycolony.core.testing.FakeCatalog;
import dev.hycolony.core.testing.FakeWorldBlocks;
import dev.hycolony.core.testing.farming.FakeFarming;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.Test;

/** MC EntityAIWorkFarmer getSurfacePos, isNoPartOfField and find(Hoeable|Plantable|Harvestable)Surface. */
class FieldScanTest {
    private static final BlockPos COLUMN = new BlockPos(1, 63, 1); // field block at y 64: cells start at y 63
    private static final BlockKey DIRT = new BlockKey("Soil_Dirt");
    private static final BlockKey WATER = new BlockKey("~fluid:Water");

    private final FakeWorldBlocks world = new FakeWorldBlocks();
    private final FakeCatalog catalog = new FakeCatalog();
    private final FakeFarming farming = new FakeFarming();
    private final FieldScan scan = new FieldScan(world, catalog, farming);

    @Test
    void surfaceIsTheTopSolidBlockWithinFive() {
        ground(60);
        assertEquals(Optional.of(new BlockPos(1, 60, 1)), scan.surface(COLUMN));
        world.blocks.clear();
        for (int y = 63; y <= 66; y++) {
            ground(y); // a hillside: the solid blocks go up from the cell
        }
        assertEquals(Optional.of(new BlockPos(1, 66, 1)), scan.surface(COLUMN));
    }

    @Test
    void noSurfaceBeyondFiveBlocks() {
        world.blocks.put(new BlockPos(1, 57, 1), new BlockState(DIRT, 0));
        assertTrue(scan.surface(COLUMN).isEmpty());
    }

    @Test
    void fluidCountsAsSolid() {
        catalog.kinds.put(WATER, BlockKind.FLUID);
        world.blocks.put(new BlockPos(1, 62, 1), new BlockState(WATER, 0));
        assertEquals(Optional.of(new BlockPos(1, 62, 1)), scan.surface(COLUMN));
    }

    @Test
    void barrierAboveExcludesTheCell() {
        BlockPos soil = ground(63);
        farming.tillable.add(soil);
        farming.barriers.add(soil.offset(0, 1, 0));
        world.blocks.put(soil.offset(0, 1, 0), new BlockState(new BlockKey("Wood_Fence"), 0));
        assertTrue(scan.hoeable(COLUMN).isEmpty());
    }

    @Test
    void tillableCellIsHoeableOnce() {
        BlockPos soil = ground(63);
        farming.tillable.add(soil);
        assertEquals(Optional.of(soil), scan.hoeable(COLUMN));
        farming.till(soil);
        assertTrue(scan.hoeable(COLUMN).isEmpty());
    }

    @Test
    void cellUnderAGrowingCropIsNotHoeable() {
        BlockPos soil = ground(63);
        farming.tillable.add(soil);
        farming.plant(soil.offset(0, 1, 0), FakeFarming.WHEAT_SEEDS);
        assertTrue(scan.hoeable(COLUMN).isEmpty());
    }

    @Test
    void untilledCellIsNotPlantable() {
        ground(63);
        assertTrue(scan.plantable(COLUMN).isEmpty());
    }

    @Test
    void tilledEmptyCellIsPlantable() {
        BlockPos soil = ground(63);
        farming.tilled.add(soil);
        assertEquals(Optional.of(soil), scan.plantable(COLUMN));
        farming.plant(soil.offset(0, 1, 0), FakeFarming.WHEAT_SEEDS);
        assertTrue(scan.plantable(COLUMN).isEmpty());
    }

    @Test
    void growingCropIsNotHarvestableButAMatureOneIs() {
        BlockPos soil = ground(63);
        farming.tilled.add(soil);
        BlockPos crop = soil.offset(0, 1, 0);
        farming.plant(crop, FakeFarming.WHEAT_SEEDS);
        assertTrue(scan.harvestable(COLUMN).isEmpty());
        farming.cropState.put(crop, CropState.MATURE);
        assertEquals(Optional.of(soil), scan.harvestable(COLUMN));
    }

    @Test
    void scanDestroysNothing() {
        BlockPos soil = ground(63);
        farming.tillable.add(soil);
        world.blocks.put(soil.offset(0, 1, 0), new BlockState(new BlockKey("Plant_Grass"), 0));
        catalog.kinds.put(new BlockKey("Plant_Grass"), BlockKind.NON_SOLID);
        Map<BlockPos, BlockState> before = Map.copyOf(world.blocks);
        Set<BlockPos> tilled = Set.copyOf(farming.tilled);

        scan.hoeable(COLUMN);
        scan.plantable(COLUMN);
        scan.harvestable(COLUMN);

        assertEquals(before, world.blocks);
        assertEquals(tilled, farming.tilled);
        assertFalse(farming.fertilized.contains(soil));
    }

    /** A dirt block at {@code y} in the column; returns its position. */
    private BlockPos ground(int y) {
        BlockPos pos = new BlockPos(1, y, 1);
        world.blocks.put(pos, new BlockState(DIRT, 0));
        return pos;
    }
}
