package dev.hycolony.core.kernel.port;

import dev.hycolony.core.kernel.BlockPos;
import dev.hycolony.core.kernel.Vec3;

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

    /** The sound of {@code soil} tilled by a hoe, as a player's till plays it. */
    void tilled(BlockPos soil);

    /** The placing sound of the block now at {@code pos}, as a player's placing plays it. */
    void blockPlaced(BlockPos pos);

    /** A sleeping citizen's particles at {@code at} (MC SleepingParticleMessage, the "zZz" over its head). */
    void sleeping(Vec3 at);

    /** An eating citizen's food crumbs at its mouth {@code at} (MC ItemParticleEffectMessage of the food eaten). */
    void eating(Vec3 at);
}
