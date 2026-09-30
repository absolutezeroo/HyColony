package dev.hycolony.core.app;

import dev.hycolony.core.colony.Colony;
import dev.hycolony.core.colony.territory.ClaimCell;
import dev.hycolony.core.kernel.BlockPos;
import dev.hycolony.core.kernel.config.ColonyConfig;

/** MC ColonyManager.isFarEnoughFromColonies: whether a new colony may be founded at a position. */
public final class ColonySpacing {
    private ColonySpacing() {}

    /**
     * True when every colony's centre is at least max(minColonyDistance, initialColonySize) claim cells away (3D, in
     * blocks, as MC's chunks) and no cell within initialColonySize of the position is claimed (MC
     * ChunkDataHelper.canClaimChunksInRange).
     */
    public static boolean isFarEnoughFromColonies(ColonyManager manager, BlockPos pos) {
        ColonyConfig.Claims claims = manager.context().config().claims();
        long range = (long) Math.max(claims.minColonyDistance(), claims.initialColonySize()) * ClaimCell.SIZE;
        for (Colony c : manager.all()) {
            if (distanceSquared(pos, c.center()) < range * range) {
                return false;
            }
        }
        return manager.territory().canClaimAround(pos, claims.initialColonySize());
    }

    private static long distanceSquared(BlockPos a, BlockPos b) {
        long dx = a.x() - b.x();
        long dy = a.y() - b.y();
        long dz = a.z() - b.z();
        return dx * dx + dy * dy + dz * dz;
    }
}
