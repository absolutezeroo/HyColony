package dev.hyangler.core.context;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.hyangler.core.port.BlockKind;
import dev.hyangler.core.testing.FakeBlocks;
import org.junit.jupiter.api.Test;

class OpenWaterTest {
    /** A pond 2 deep under the bobber at (0, 10, 0): water at y 9 and 10, air above. */
    private static FakeBlocks pond() {
        return new FakeBlocks().fill(-5, 5, -5, 5, 10, 5, BlockKind.WATER_SOURCE);
    }

    @Test
    void aWidePondIsOpenWater() {
        assertTrue(OpenWater.test(pond(), 0, 10, 0));
    }

    @Test
    void aBankWithinTwoBlocksIsNot() {
        FakeBlocks narrow = pond().fill(2, 9, -5, 2, 10, 5, BlockKind.OTHER);
        assertFalse(OpenWater.test(narrow, 0, 10, 0));
    }

    @Test
    void flowingWaterIsNot() {
        FakeBlocks river = new FakeBlocks().fill(-5, 5, -5, 5, 10, 5, BlockKind.WATER_FLOWING);
        assertFalse(OpenWater.test(river, 0, 10, 0));
    }

    @Test
    void aBlockAboveTheSurfaceIsNot() {
        FakeBlocks bridge = pond().fill(-1, 12, -1, 1, 12, 1, BlockKind.OTHER);
        assertFalse(OpenWater.test(bridge, 0, 10, 0));
    }

    @Test
    void aBobberOnAShallowPuddleIsNot() {
        FakeBlocks puddle = new FakeBlocks().fill(-5, 10, -5, 5, 10, 5, BlockKind.WATER_SOURCE);
        assertFalse(OpenWater.test(puddle, 0, 10, 0));
    }
}
