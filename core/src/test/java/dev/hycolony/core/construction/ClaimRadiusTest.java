package dev.hycolony.core.construction;

import static org.junit.jupiter.api.Assertions.assertEquals;

import dev.hycolony.core.building.BuildingTypes;
import dev.hycolony.core.colony.ClaimCell;
import dev.hycolony.core.colony.TerritoryIndex;
import dev.hycolony.core.kernel.BlockPos;
import java.util.OptionalInt;
import org.junit.jupiter.api.Test;

class ClaimRadiusTest {
    @Test
    void claimRadiusTables() {
        String townHall = BuildingTypes.TOWN_HALL.id();
        int[] expectedTownHall = {0, 1, 1, 2, 3, 5};
        for (int level = 0; level <= 5; level++) {
            assertEquals(expectedTownHall[level], ClaimRadius.of(townHall, level), "town hall level " + level);
        }

        int[] expectedDefault = {0, 1, 1, 1, 2, 2};
        for (String id : new String[] {"hycolony:builder", "hycolony:residence", "hycolony:anything-else"}) {
            for (int level = 0; level <= 5; level++) {
                assertEquals(expectedDefault[level], ClaimRadius.of(id, level), id + " level " + level);
            }
        }
    }

    @Test
    void claimBoundedByMaxColonySize() {
        TerritoryIndex t = new TerritoryIndex();
        ClaimCell colonyCenter = new ClaimCell(0, 0);
        ClaimCell buildingCell = new ClaimCell(5, 0);

        // Pre-claimed by another colony, inside the region that would otherwise be claimed: must not be stolen.
        t.claimSquareBounded(2, new ClaimCell(3, 0), 0, colonyCenter, 20);

        // Radius 3 around (5,0) reaches x in [2,8]; bounded to maxSize 6 keeps only x in [-6,6].
        t.claimSquareBounded(1, buildingCell, 3, colonyCenter, 6);

        assertEquals(OptionalInt.of(1), t.colonyAt(cellPos(6, 0)));
        assertEquals(OptionalInt.empty(), t.colonyAt(cellPos(7, 0)));
        assertEquals(OptionalInt.empty(), t.colonyAt(cellPos(8, 0)));
        // Never stolen from colony 2.
        assertEquals(OptionalInt.of(2), t.colonyAt(cellPos(3, 0)));
    }

    private static BlockPos cellPos(int cellX, int cellZ) {
        return new BlockPos(cellX * ClaimCell.SIZE, 64, cellZ * ClaimCell.SIZE);
    }
}
