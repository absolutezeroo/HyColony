package dev.hycolony.core.colony;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.hycolony.core.kernel.BlockPos;
import java.util.OptionalInt;
import org.junit.jupiter.api.Test;

class TerritoryIndexTest {
    @Test
    void cellOfNegativeCoordinatesFloors() {
        assertEquals(new ClaimCell(-1, 0), ClaimCell.of(new BlockPos(-1, 64, 15)));
        assertEquals(new ClaimCell(1, -2), ClaimCell.of(new BlockPos(16, 0, -17)));
    }

    @Test
    void initialClaimIsNineByNineCells() {
        TerritoryIndex t = new TerritoryIndex();
        t.claimSquare(1, ClaimCell.of(new BlockPos(0, 64, 0)), 4);
        assertEquals(81, t.claimedCount(1));
        assertEquals(OptionalInt.of(1), t.colonyAt(new BlockPos(4 * 16 + 15, 0, -4 * 16)));
        assertEquals(OptionalInt.empty(), t.colonyAt(new BlockPos(5 * 16, 0, 0)));
    }

    @Test
    void claimNeverStealsCells() {
        TerritoryIndex t = new TerritoryIndex();
        t.claimSquare(1, new ClaimCell(0, 0), 1);
        t.claimSquare(2, new ClaimCell(2, 0), 1);
        assertEquals(OptionalInt.of(1), t.colonyAt(new BlockPos(16, 0, 0)));
        assertEquals(6, t.claimedCount(2));
    }

    @Test
    void newColonyNeedsInitialPlusMinDistanceFree() {
        TerritoryIndex t = new TerritoryIndex();
        t.claimSquare(1, new ClaimCell(0, 0), 4); // cells -4..4
        assertFalse(t.isFreeForNewColony(new BlockPos(16 * 16, 64, 0), 4, 8)); // centre cell 16: 16-12 = 4 claimed
        assertTrue(t.isFreeForNewColony(new BlockPos(17 * 16, 64, 0), 4, 8));  // 17-12 = 5 free
    }

    @Test
    void releaseFreesCells() {
        TerritoryIndex t = new TerritoryIndex();
        t.claimSquare(1, new ClaimCell(0, 0), 2);
        t.releaseAll(1);
        assertEquals(0, t.claimedCount(1));
        assertEquals(OptionalInt.empty(), t.colonyAt(new BlockPos(0, 0, 0)));
    }
}
