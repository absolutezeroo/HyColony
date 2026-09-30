package dev.hycolony.core.app;

import dev.hycolony.core.colony.Colony;
import dev.hycolony.core.colony.territory.ClaimCell;
import dev.hycolony.core.kernel.BlockPos;
import dev.hycolony.core.kernel.config.ColonyConfig;
import java.util.Comparator;
import java.util.Optional;

/** MC ColonyManager.isFarEnoughFromColonies: whether a new colony may be founded at a position. */
public final class ColonySpacing {
    private ColonySpacing() {}

    /**
     * True when the closest colony (MC getClosestColony: the owner of the position's cell, else the nearest centre in
     * 2D) has its centre at least max(minColonyDistance, initialColonySize) claim cells away (3D, in blocks, as MC's
     * chunks), and no cell within initialColonySize of the position is claimed (MC canClaimChunksInRange).
     */
    public static boolean isFarEnoughFromColonies(ColonyManager manager, BlockPos pos) {
        ColonyConfig.Claims claims = manager.context().config().claims();
        long range = (long) Math.max(claims.minColonyDistance(), claims.initialColonySize()) * ClaimCell.SIZE;
        Optional<Colony> closest = manager.colonyAt(pos)
                .or(() -> manager.all().stream().min(Comparator.comparingLong(c -> squared(pos, c.center(), false))));
        if (closest.isPresent() && squared(pos, closest.get().center(), true) < range * range) {
            return false;
        }
        return manager.territory().canClaimAround(pos, claims.initialColonySize());
    }

    private static long squared(BlockPos a, BlockPos b, boolean withHeight) {
        long dx = a.x() - b.x();
        long dy = withHeight ? a.y() - b.y() : 0;
        long dz = a.z() - b.z();
        return dx * dx + dy * dy + dz * dz;
    }
}
