package dev.hycolony.plugin.adapter;

import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.logger.HytaleLogger;
import com.hypixel.hytale.protocol.BlockParticleEvent;
import com.hypixel.hytale.protocol.BlockSoundEvent;
import com.hypixel.hytale.protocol.SoundCategory;
import com.hypixel.hytale.server.core.asset.type.blocksound.config.BlockSoundSet;
import com.hypixel.hytale.server.core.asset.type.blocktype.config.BlockType;
import com.hypixel.hytale.server.core.asset.type.soundevent.config.SoundEvent;
import com.hypixel.hytale.server.core.modules.blockhealth.BlockHealthSection;
import com.hypixel.hytale.server.core.modules.time.TimeResource;
import com.hypixel.hytale.server.core.universe.world.ParticleUtil;
import com.hypixel.hytale.server.core.universe.world.SoundUtil;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.chunk.section.BlockSection;
import com.hypixel.hytale.server.core.universe.world.chunk.section.ChunkSection;
import com.hypixel.hytale.server.core.universe.world.storage.ChunkStore;
import dev.hycolony.core.kernel.BlockPos;
import dev.hycolony.core.kernel.Vec3;
import dev.hycolony.core.kernel.port.WorldEffects;
import java.util.List;
import java.util.Optional;
import java.util.logging.Level;
import org.joml.Vector3d;
import org.jspecify.annotations.Nullable;

/**
 * Vanilla firework particle systems, block hit feedback, the till sound and block placing sounds, sent to the nearby
 * players. World thread only; never throws (first failure WARNING, then FINE).
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
    private final Optional<String> tillSound;
    private boolean warned;

    public HytaleWorldEffects(World world, List<String> fireworks, Optional<String> tillSound) {
        this.world = world;
        this.fireworks = fireworks;
        this.tillSound = tillSound;
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
            int id = blockId(pos);
            BlockType type = BlockType.getAssetMap().getAsset(id);
            if (id == 0 || type == null) {
                return;
            }
            playBlockSound(pos, type, BlockSoundEvent.Hit);
            world.getNotificationHandler()
                    .sendBlockParticle(pos.x() + 0.5, pos.y() + 0.5, pos.z() + 0.5, id, BlockParticleEvent.Hit);
            crack(pos, progress);
        } catch (RuntimeException e) {
            LOG.at(warned ? Level.FINE : Level.WARNING).withCause(e).log("HyColony block hit failed at %s", pos);
            warned = true;
        }
    }

    /**
     * The block's own Build sound (a crop's is the seeds' SFX_Seeds_Place), as a player's placing plays it; the
     * placing itself already sent the Build particles. No-op on an unloaded or empty block.
     */
    @Override
    public void blockPlaced(BlockPos pos) {
        try {
            int id = blockId(pos);
            BlockType type = BlockType.getAssetMap().getAsset(id);
            if (id != 0 && type != null) {
                playBlockSound(pos, type, BlockSoundEvent.Build);
            }
        } catch (RuntimeException e) {
            LOG.at(warned ? Level.FINE : Level.WARNING).withCause(e).log("HyColony place sound failed at %s", pos);
            warned = true;
        }
    }

    /** The block id at {@code pos}; 0 (empty) on an unloaded section. */
    private int blockId(BlockPos pos) {
        Ref<ChunkStore> sec = section(pos);
        BlockSection blocks = sec == null
                ? null
                : world.getChunkStore().getStore().getComponent(sec, BlockSection.getComponentType());
        return blocks == null ? 0 : blocks.get(pos.x(), pos.y(), pos.z());
    }

    /** The loaded section holding {@code pos}; null when it is not loaded. */
    private @Nullable Ref<ChunkStore> section(BlockPos pos) {
        Ref<ChunkStore> sec = world.getChunkStore().getChunkSectionReferenceAtBlock(pos.x(), pos.y(), pos.z());
        return sec == null || !sec.isValid() ? null : sec;
    }

    /** {@code event} of the block's sound set at the block's centre; nothing when the set has none. */
    private void playBlockSound(BlockPos pos, BlockType type, BlockSoundEvent event) {
        BlockSoundSet sounds = BlockSoundSet.getAssetMap().getAsset(type.getBlockSoundSetIndex());
        if (sounds == null) {
            return;
        }
        SoundUtil.playSoundEvent3d(
                sounds.getSoundEventIndices().getOrDefault(event, 0),
                SoundCategory.SFX,
                pos.x() + 0.5,
                pos.y() + 0.5,
                pos.z() + 0.5,
                world.getEntityStore().getStore());
    }

    /**
     * The till sound at the soil's centre, as ChangeBlockInteraction plays Hoe_Till's WorldSoundEventId; nothing when
     * the id-map names none or the game does not know it.
     */
    @Override
    public void tilled(BlockPos soil) {
        try {
            int sound = tillSound.map(SoundEvent.getAssetMap()::getIndex).orElse(Integer.MIN_VALUE);
            if (sound == Integer.MIN_VALUE) {
                return;
            }
            SoundUtil.playSoundEvent3d(
                    sound,
                    SoundCategory.SFX,
                    soil.x() + 0.5,
                    soil.y() + 0.5,
                    soil.z() + 0.5,
                    world.getEntityStore().getStore());
        } catch (RuntimeException e) {
            LOG.at(warned ? Level.FINE : Level.WARNING).withCause(e).log("HyColony till sound failed at %s", soil);
            warned = true;
        }
    }

    /**
     * Lowers the block's shared health to {@code 1 - progress} (never below {@link #MIN_HEALTH}, since damage to 0
     * drops the entry), as BlockHarvestUtils damages a section's health; BlockHealthSystems replicates the cracks to
     * the players with the section. The block regenerates 5 s after the last hit, so an abandoned block mends.
     */
    private void crack(BlockPos pos, float progress) {
        Ref<ChunkStore> sec = section(pos);
        if (sec == null) {
            return;
        }
        Store<ChunkStore> chunks = world.getChunkStore().getStore();
        ChunkSection section = chunks.getComponent(sec, ChunkSection.getComponentType());
        if (section == null) {
            return;
        }
        BlockHealthSection health = chunks.getComponent(sec, BlockHealthSection.getComponentType());
        if (health == null) {
            return;
        }
        float damage = health.getHealth(pos.x(), pos.y(), pos.z()) - Math.max(MIN_HEALTH, 1f - progress);
        if (damage > 0) {
            TimeResource time = world.getEntityStore().getStore().getResource(TimeResource.getResourceType());
            health.damage(pos.x(), pos.y(), pos.z(), damage, time.getNow());
            section.markNeedsSaving();
        }
    }

    /** Not yet wired to Hytale's sleep particles (SP4 plan Task 17). */
    @Override
    public void sleeping(Vec3 at) {}
}
