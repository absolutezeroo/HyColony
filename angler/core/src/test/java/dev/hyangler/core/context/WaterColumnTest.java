package dev.hyangler.core.context;

import static org.junit.jupiter.api.Assertions.assertEquals;

import dev.hyangler.core.port.BlockKind;
import dev.hyangler.core.testing.FakeBlocks;
import org.junit.jupiter.api.Test;

class WaterColumnTest {

    @Test
    void depthCountsTheWaterBelowTheBobberItselfIncluded() {
        FakeBlocks lake = new FakeBlocks().fill(0, 6, 0, 0, 10, 0, BlockKind.WATER_SOURCE);
        assertEquals(5, WaterColumn.depth(lake, 0, 10, 0));
    }

    @Test
    void flowingWaterCountsToo() {
        FakeBlocks river = new FakeBlocks().fill(0, 8, 0, 0, 10, 0, BlockKind.WATER_FLOWING);
        assertEquals(3, WaterColumn.depth(river, 0, 10, 0));
    }

    @Test
    void theScanStopsAtItsLimit() {
        FakeBlocks ocean = new FakeBlocks().fill(0, -100, 0, 0, 10, 0, BlockKind.WATER_SOURCE);
        assertEquals(WaterColumn.SCAN_LIMIT, WaterColumn.depth(ocean, 0, 10, 0));
    }

    @Test
    void noWaterUnderTheBobberIsZero() {
        assertEquals(0, WaterColumn.depth(new FakeBlocks(), 0, 10, 0));
    }
}
