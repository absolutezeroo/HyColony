package dev.hycolony.plugin.adapter;

import com.hypixel.hytale.logger.HytaleLogger;
import com.hypixel.hytale.server.core.universe.world.ParticleUtil;
import com.hypixel.hytale.server.core.universe.world.World;
import dev.hycolony.core.kernel.BlockPos;
import dev.hycolony.core.kernel.port.WorldEffects;
import java.util.List;
import java.util.logging.Level;
import org.joml.Vector3d;

/**
 * Vanilla firework particle systems, sent to the players within {@link ParticleUtil#DEFAULT_PARTICLE_DISTANCE}. World
 * thread only.
 */
public final class HytaleWorldEffects implements WorldEffects {
    private static final HytaleLogger LOG = HytaleLogger.forEnclosingClass();
    /** Blocks above the hut block where the fireworks burst. */
    private static final int HEIGHT = 8;
    /** Horizontal distance, in blocks, from the hut to each firework. */
    private static final int SPREAD = 3;

    private final World world;
    private final List<String> fireworks;
    private boolean warned;

    public HytaleWorldEffects(World world, List<String> fireworks) {
        this.world = world;
        this.fireworks = fireworks;
    }

    /**
     * One firework over each corner around the hut, as MC FireworkUtils.spawnFireworksAtAABBCorners fires one rocket
     * per corner of the building. Deviation from MC: corners of a fixed square around the hut block (the core has no
     * building box), and no sky check; the systems' own StartDelay staggers the bursts.
     */
    @Override
    public void celebrate(BlockPos hut) {
        try {
            int i = 0;
            for (int dx = -SPREAD; dx <= SPREAD; dx += 2 * SPREAD) {
                for (int dz = -SPREAD; dz <= SPREAD; dz += 2 * SPREAD) {
                    Vector3d at = new Vector3d(hut.x() + 0.5 + dx, hut.y() + HEIGHT, hut.z() + 0.5 + dz);
                    ParticleUtil.spawnParticleEffect(
                            fireworks.get(i++ % fireworks.size()),
                            at,
                            world.getEntityStore().getStore());
                }
            }
        } catch (RuntimeException e) {
            LOG.at(warned ? Level.FINE : Level.WARNING).withCause(e).log("HyColony fireworks failed");
            warned = true;
        }
    }

    /** No block hit feedback yet. */
    @Override
    public void blockHit(BlockPos pos, float progress) {}
}
