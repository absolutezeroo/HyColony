package dev.hycolony.plugin.adapter;

import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.logger.HytaleLogger;
import com.hypixel.hytale.protocol.BlockParticleEvent;
import com.hypixel.hytale.protocol.BlockSoundEvent;
import com.hypixel.hytale.protocol.SoundCategory;
import com.hypixel.hytale.server.core.asset.type.blocksound.config.BlockSoundSet;
import com.hypixel.hytale.server.core.asset.type.blocktype.config.BlockType;
import com.hypixel.hytale.server.core.modules.blockhealth.BlockHealth;
import com.hypixel.hytale.server.core.modules.blockhealth.BlockHealthChunk;
import com.hypixel.hytale.server.core.modules.blockhealth.BlockHealthModule;
import com.hypixel.hytale.server.core.modules.time.TimeResource;
import com.hypixel.hytale.server.core.universe.world.ParticleUtil;
import com.hypixel.hytale.server.core.universe.world.SoundUtil;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.chunk.section.BlockSection;
import com.hypixel.hytale.server.core.universe.world.chunk.section.ChunkSection;
import com.hypixel.hytale.server.core.universe.world.storage.ChunkStore;
import dev.hycolony.core.kernel.BlockPos;
import dev.hycolony.core.kernel.port.WorldEffects;
import java.util.List;
import java.util.logging.Level;
import org.joml.Vector3d;
import org.joml.Vector3i;

/**
 * Vanilla firework particle systems and block hit feedback, sent to the nearby players. World thread only; never
 * throws (first failure WARNING, then FINE).
 */
public final class HytaleWorldEffects implements WorldEffects {
    private static final HytaleLogger LOG = HytaleLogger.forEnclosingClass();
    /** Blocks above the hut block where the fireworks burst. */
    private static final int HEIGHT = 8;
    /** Horizontal distance, in blocks, from the hut to each firework. */
    private static final int SPREAD = 3;
    /** Lowest health the cracks show: the block never breaks by damage, {@code breakBlock} removes it. */
    private static final float MIN_HEALTH = 0.05f;

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

    /**
     * The block's own Hit sound and Hit particles, as a player's stroke plays them (BlockHarvestUtils.playBlockSound),
     * then cracks up to {@code progress}. No-op on an unloaded or empty block.
     *
     * <p>Deviation from MC: the sound plays at Hytale's player-hit modifiers (1, 1). MC's (v + 1) x 0.125 and
     * p x 0.5 are Minecraft's own player-hit values, which Hytale's asset volumes replace. The cracks are an addition:
     * MC workers never call destroyBlockProgress.
     */
    @Override
    public void blockHit(BlockPos pos, float progress) {
        try {
            Ref<ChunkStore> sec = world.getChunkStore().getChunkSectionReferenceAtBlock(pos.x(), pos.y(), pos.z());
            if (sec == null || !sec.isValid()) {
                return;
            }
            Store<ChunkStore> chunks = world.getChunkStore().getStore();
            BlockSection blocks = chunks.getComponent(sec, BlockSection.getComponentType());
            int id = blocks == null ? 0 : blocks.get(pos.x(), pos.y(), pos.z());
            BlockType type = BlockType.getAssetMap().getAsset(id);
            if (id == 0 || type == null) {
                return;
            }
            double x = pos.x() + 0.5, y = pos.y() + 0.5, z = pos.z() + 0.5;
            BlockSoundSet sounds = BlockSoundSet.getAssetMap().getAsset(type.getBlockSoundSetIndex());
            if (sounds != null) {
                int sound = sounds.getSoundEventIndices().getOrDefault(BlockSoundEvent.Hit, 0);
                SoundUtil.playSoundEvent3d(
                        sound,
                        SoundCategory.SFX,
                        x,
                        y,
                        z,
                        world.getEntityStore().getStore());
            }
            world.getNotificationHandler().sendBlockParticle(x, y, z, id, BlockParticleEvent.Hit);
            crack(chunks, sec, pos, progress);
        } catch (RuntimeException e) {
            LOG.at(warned ? Level.FINE : Level.WARNING).withCause(e).log("HyColony block hit failed at %s", pos);
            warned = true;
        }
    }

    /**
     * Lowers the block's shared health to {@code 1 - progress} (never below {@link #MIN_HEALTH}), which shows the
     * cracks to every player with the chunk. BlockHealthModule heals it 5 s after the last hit, so an abandoned block
     * mends; naturallyRemoveBlock clears it on the break.
     */
    private void crack(Store<ChunkStore> chunks, Ref<ChunkStore> sec, BlockPos pos, float progress) {
        ChunkSection section = chunks.getComponent(sec, ChunkSection.getComponentType());
        if (section == null) {
            return;
        }
        BlockHealthChunk health = chunks.getComponent(
                section.getChunkColumnReference(), BlockHealthModule.get().getBlockHealthChunkComponentType());
        if (health == null) {
            return;
        }
        // A new key each time: the chunk keeps it in its map.
        Vector3i at = new Vector3i(pos.x(), pos.y(), pos.z());
        BlockHealth now = health.getBlockHealthMap().get(at);
        float current = now == null ? 1f : now.getHealth();
        float damage = current - Math.max(MIN_HEALTH, 1f - progress);
        if (damage > 0) {
            TimeResource time = world.getEntityStore().getStore().getResource(TimeResource.getResourceType());
            health.damageBlock(time.getNow(), world, at, damage);
        }
    }
}
