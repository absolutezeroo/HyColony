package dev.hycolony.core.building;

import static org.junit.jupiter.api.Assertions.assertEquals;

import dev.hycolony.core.kernel.BlockPos;
import java.util.List;
import org.junit.jupiter.api.Test;

class BuildingTest {
    @Test
    void maxEquipmentLevelFollowsMcTable() {
        Building b = Building.create(new BuildingType("t", "hut.t", 5, List.of()), new BlockPos(0, 0, 0), 0);
        int[] expected = {1, 1, 2, 3, 4, Integer.MAX_VALUE};
        for (int level = 0; level <= 5; level++) {
            b.setLevel(level);
            assertEquals(expected[level], b.maxEquipmentLevel(), "level " + level);
        }
    }
}
