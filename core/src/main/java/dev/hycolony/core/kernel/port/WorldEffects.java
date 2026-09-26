package dev.hycolony.core.kernel.port;

import dev.hycolony.core.kernel.BlockPos;

/** Purely visual world effects seen by nearby players. Never throws; an unloaded position is a no-op. */
public interface WorldEffects {
    /** Fireworks above {@code hut} (MC FireworkUtils.spawnFireworksAtAABBCorners). */
    void celebrate(BlockPos hut);
}
