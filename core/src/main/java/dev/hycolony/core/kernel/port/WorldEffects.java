package dev.hycolony.core.kernel.port;

import dev.hycolony.core.kernel.BlockPos;

/** Purely visual world effects seen by nearby players. Never throws; an unloaded position is a no-op. */
public interface WorldEffects {
    /** Fireworks above {@code hut} (MC FireworkUtils.spawnFireworksAtAABBCorners). */
    void celebrate(BlockPos hut);

    /**
     * One tool stroke on the block at {@code pos}: its hit sound and particles (MC
     * CitizenItemUtils.hitBlockWithToolInHand(citizen, pos, false)). {@code progress} runs from 0 to 1 over the break
     * delay; the block itself stays in place.
     */
    void blockHit(BlockPos pos, float progress);
}
