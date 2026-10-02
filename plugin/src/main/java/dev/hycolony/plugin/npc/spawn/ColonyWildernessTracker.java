package dev.hycolony.plugin.npc.spawn;

import com.hypixel.hytale.builtin.adventure.wilderness.resource.WildernessTracker;
import com.hypixel.hytale.logger.HytaleLogger;
import com.hypixel.hytale.math.util.ChunkUtil;
import com.hypixel.hytale.server.core.universe.world.World;
import dev.hycolony.plugin.WorldRuntime;
import dev.hycolony.plugin.WorldRuntimes;
import java.util.logging.Level;
import javax.annotation.Nonnull;
import org.joml.Vector3i;

/**
 * The world's wilderness tracker, minus the colonies (ColonyProtection.isWilderness): Hytale opens its world events
 * (goblin breaches) only in chunks its players' Wilderness component marks as wilderness, and Wilderness.move asks
 * only {@link #isWildernessChunk(Vector3i)}. Installed in place of the vanilla tracker by
 * {@link WildernessTrackerSystem}; on the world thread.
 */
final class ColonyWildernessTracker extends WildernessTracker {
    private static final HytaleLogger LOG = HytaleLogger.forEnclosingClass();

    private final WorldRuntimes runtimes;
    private final World world;
    /** The first failure is logged SEVERE, the next ones FINE. */
    private boolean failed;

    /** A copy of {@code vanilla} (its settings and home chunks) that also leaves out the colonies of {@code world}. */
    ColonyWildernessTracker(WildernessTracker vanilla, WorldRuntimes runtimes, World world) {
        super(vanilla);
        this.runtimes = runtimes;
        this.world = world;
        // the copy starts at 0: a value that differs from the replaced tracker's makes the players recompute
        generation.set(vanilla.generation() + 1);
    }

    /** Grows with the home chunks and with the colonies' territory, so Wilderness.move recomputes on either. */
    @Override
    public long generation() {
        WorldRuntime rt = runtimes.of(world);
        return super.generation() + (rt == null ? 0 : rt.manager().territory().revision());
    }

    /** Wilderness for Hytale and touching no colony; Hytale's answer alone if the colonies cannot be read. */
    @Override
    public boolean isWildernessChunk(@Nonnull Vector3i coords) {
        if (!super.isWildernessChunk(coords)) {
            return false;
        }
        try {
            WorldRuntime rt = runtimes.of(world);
            return rt == null
                    || rt.manager()
                            .protection()
                            .isWilderness(coords.x * ChunkUtil.SIZE, coords.z * ChunkUtil.SIZE, ChunkUtil.SIZE);
        } catch (RuntimeException e) {
            LOG.at(failed ? Level.FINE : Level.SEVERE).withCause(e).log("HyColony wilderness check failed");
            failed = true;
            return true;
        }
    }

    @Nonnull
    @Override
    public WildernessTracker clone() {
        return new ColonyWildernessTracker(this, runtimes, world);
    }
}
