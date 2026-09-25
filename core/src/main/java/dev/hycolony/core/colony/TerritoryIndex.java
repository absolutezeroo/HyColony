package dev.hycolony.core.colony;

import dev.hycolony.core.kernel.BlockPos;
import java.util.HashMap;
import java.util.Map;
import java.util.OptionalInt;

/** Which colony owns which claim cell, for one world. Rebuilt from colonies on load. */
public final class TerritoryIndex {
    private final Map<ClaimCell, Integer> owners = new HashMap<>();

    public OptionalInt colonyAt(BlockPos pos) {
        Integer id = owners.get(ClaimCell.of(pos));
        return id == null ? OptionalInt.empty() : OptionalInt.of(id);
    }

    /** Claims every free cell in the square of the given radius. Never steals a cell. */
    public void claimSquare(int colonyId, ClaimCell center, int radius) {
        for (int dx = -radius; dx <= radius; dx++) {
            for (int dz = -radius; dz <= radius; dz++) {
                owners.putIfAbsent(new ClaimCell(center.x() + dx, center.z() + dz), colonyId);
            }
        }
    }

    public void releaseAll(int colonyId) {
        owners.values().removeIf(id -> id == colonyId);
    }

    /** No claimed cell within (initialSize + minDistance) cells of the would-be centre. */
    public boolean isFreeForNewColony(BlockPos center, int initialSize, int minDistance) {
        ClaimCell c = ClaimCell.of(center);
        int r = initialSize + minDistance;
        for (int dx = -r; dx <= r; dx++) {
            for (int dz = -r; dz <= r; dz++) {
                if (owners.containsKey(new ClaimCell(c.x() + dx, c.z() + dz))) {
                    return false;
                }
            }
        }
        return true;
    }

    public int claimedCount(int colonyId) {
        return (int) owners.values().stream().filter(id -> id == colonyId).count();
    }
}
