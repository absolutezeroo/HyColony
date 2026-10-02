package dev.hycolony.core.colony.territory;

import dev.hycolony.core.kernel.BlockPos;
import java.util.HashMap;
import java.util.Map;
import java.util.OptionalInt;

/** Which colony owns which claim cell, for one world. Rebuilt from colonies on load. */
public final class TerritoryIndex {
    private final Map<ClaimCell, Integer> owners = new HashMap<>();

    public OptionalInt colonyAt(BlockPos pos) {
        return colonyAt(ClaimCell.of(pos));
    }

    /** The colony owning {@code cell}; empty if unclaimed. */
    public OptionalInt colonyAt(ClaimCell cell) {
        Integer id = owners.get(cell);
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

    /**
     * Like {@link #claimSquare}, but only claims cells within {@code maxSize} (Chebyshev distance)
     * of {@code colonyCenter}. Never steals a cell.
     */
    public void claimSquareBounded(int colonyId, ClaimCell center, int radius, ClaimCell colonyCenter, int maxSize) {
        for (int dx = -radius; dx <= radius; dx++) {
            for (int dz = -radius; dz <= radius; dz++) {
                ClaimCell cell = new ClaimCell(center.x() + dx, center.z() + dz);
                int distFromCenter =
                        Math.max(Math.abs(cell.x() - colonyCenter.x()), Math.abs(cell.z() - colonyCenter.z()));
                if (distFromCenter > maxSize) {
                    continue;
                }
                owners.putIfAbsent(cell, colonyId);
            }
        }
    }

    public void releaseAll(int colonyId) {
        owners.values().removeIf(id -> id == colonyId);
    }

    /** MC ChunkDataHelper.canClaimChunksInRange: no claimed cell within {@code range} cells of {@code center}'s. */
    public boolean canClaimAround(BlockPos center, int range) {
        ClaimCell c = ClaimCell.of(center);
        int r = range;
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
