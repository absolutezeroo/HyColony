package dev.hycolony.plugin.npc.spawn;

import com.hypixel.hytale.builtin.adventure.wilderness.WildernessPlugin;
import com.hypixel.hytale.builtin.adventure.wilderness.resource.WildernessTracker;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.component.system.tick.TickingSystem;
import com.hypixel.hytale.logger.HytaleLogger;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import dev.hycolony.plugin.WorldRuntimes;
import java.util.logging.Level;
import javax.annotation.Nonnull;

/**
 * Keeps a {@link ColonyWildernessTracker} as the wilderness tracker of every world HyColony runs in: Hytale replaces
 * the tracker when the world starts and when its gameplay config reloads (WildernessTrackerSystems.reload), so each
 * tick puts ours back over a new vanilla one; until then, for at most a tick, players may see the vanilla wilderness.
 * Does nothing without the Wilderness plugin.
 */
final class WildernessTrackerSystem extends TickingSystem<EntityStore> {
    private static final HytaleLogger LOG = HytaleLogger.forEnclosingClass();

    private final WorldRuntimes runtimes;
    /** The first failure is logged SEVERE, the next ones FINE (one per tick otherwise). */
    private boolean failed;

    WildernessTrackerSystem(WorldRuntimes runtimes) {
        this.runtimes = runtimes;
    }

    /** Replaces the world's tracker with a colony-aware copy if it is not one yet; never throws. */
    @Override
    public void tick(float dt, int systemIndex, @Nonnull Store<EntityStore> store) {
        try {
            World world = store.getExternalData().getWorld();
            if (WildernessPlugin.get() == null || runtimes.of(world) == null) {
                return;
            }
            WildernessTracker tracker = WildernessTracker.getTracker(world);
            if (!(tracker instanceof ColonyWildernessTracker)) {
                world.getChunkStore()
                        .getStore()
                        .replaceResource(
                                WildernessTracker.getResourceType(),
                                new ColonyWildernessTracker(tracker, runtimes, world));
            }
        } catch (RuntimeException e) { // out of a TickingSystem, it would stop the world's thread
            LOG.at(failed ? Level.FINE : Level.SEVERE).withCause(e).log("HyColony could not set the wilderness");
            failed = true;
        }
    }
}
