package dev.hycolony.core.kernel;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class BlockPosTest {
    @Test
    void offsetAndDistance() {
        BlockPos p = new BlockPos(1, 2, 3);
        assertEquals(new BlockPos(2, 2, 1), p.offset(1, 0, -2));
        assertEquals(1 + 0 + 4, p.distSq(new BlockPos(2, 2, 1)));
    }

    @Test
    void vecFloorsToBlockPos() {
        assertEquals(new BlockPos(-1, 0, 2), new Vec3(-0.5, 0.9, 2.1).toBlockPos());
        assertEquals(new Vec3(1.5, 2, 3.5), Vec3.center(new BlockPos(1, 2, 3)));
    }
}
