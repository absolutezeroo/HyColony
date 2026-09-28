package dev.hycolony.core.construction.blueprint;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.hycolony.core.kernel.BlockPos;
import dev.hycolony.core.kernel.item.BlockKey;
import dev.hycolony.core.kernel.item.BlockState;
import dev.hycolony.core.kernel.item.Workstation;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class BlueprintEntryTest {
    private static final BlockState BENCH = new BlockState(new BlockKey("Bench_Farming"), 0);

    @Test
    void plainBlockCarriesNoWorkstation() {
        assertTrue(new BlueprintEntry(new BlockPos(0, 0, 0), BENCH, false)
                .workstation()
                .isEmpty());
    }

    @Test
    void benchCellCarriesItsWorkstation() {
        Workstation tier2 = new Workstation("Farmingbench", 2);
        BlueprintEntry e = new BlueprintEntry(new BlockPos(0, 0, 0), BENCH, false, Optional.of(tier2));
        assertEquals(Optional.of(tier2), e.workstation());
    }
}
