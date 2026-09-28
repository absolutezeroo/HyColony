package dev.hycolony.core.testing.farming;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.hycolony.core.farming.CropState;
import dev.hycolony.core.kernel.BlockPos;
import dev.hycolony.core.kernel.item.ItemKey;
import org.junit.jupiter.api.Test;

/** The farming fake follows the Hytale rules the farmer relies on (sp3b-hytale-farming § 2-3). */
class FakeFarmingTest {
    private static final BlockPos SOIL = new BlockPos(0, 63, 0);
    private static final BlockPos CROP = SOIL.offset(0, 1, 0);

    @Test
    void tillThenPlantThenHarvestEmptiesANormalCrop() {
        FakeFarming f = new FakeFarming();
        f.tillable.add(SOIL);

        assertTrue(f.till(SOIL));
        assertTrue(f.isTilled(SOIL));
        assertTrue(f.plant(CROP, FakeFarming.WHEAT_SEEDS));
        assertEquals(CropState.GROWING, f.crop(CROP));
        f.cropState.put(CROP, CropState.MATURE);

        assertEquals(2, f.harvest(CROP).size());
        assertEquals(CropState.NONE, f.crop(CROP));
    }

    @Test
    void eternalCropRegrowsAfterHarvest() {
        FakeFarming f = new FakeFarming();
        f.plant(CROP, new ItemKey("Plant_Seeds_Wheat_Eternal"));
        f.cropState.put(CROP, CropState.MATURE);

        f.harvest(CROP);

        assertEquals(CropState.GROWING, f.crop(CROP));
    }

    @Test
    void plantRefusesAnOccupiedCell() {
        FakeFarming f = new FakeFarming();
        assertTrue(f.plant(CROP, FakeFarming.WHEAT_SEEDS));
        assertFalse(f.plant(CROP, FakeFarming.WHEAT_SEEDS));
    }
}
