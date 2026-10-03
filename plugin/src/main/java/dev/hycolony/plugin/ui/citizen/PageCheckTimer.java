package dev.hycolony.plugin.ui.citizen;

import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.logger.HytaleLogger;
import com.hypixel.hytale.server.core.HytaleServer;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import org.jspecify.annotations.Nullable;

/**
 * Runs a page's check on its world every {@link #CHECK_MILLIS}, for what no event reports (a citizen's AI, its
 * health). When that world stops, the checks end and the page's stop runs on the world the player is in now.
 */
final class PageCheckTimer {
    private static final HytaleLogger LOG = HytaleLogger.forEnclosingClass();
    /** How often the check runs, in milliseconds. */
    private static final long CHECK_MILLIS = 500;

    private final World world;
    private final PlayerRef player;
    private final Runnable check;
    private final Runnable stop;
    private @Nullable ScheduledFuture<?> timer;

    /** {@code check} runs on {@code world}'s thread; {@code stop} on the player's world once {@code world} stops. */
    PageCheckTimer(World world, PlayerRef player, Runnable check, Runnable stop) {
        this.world = world;
        this.player = player;
        this.check = check;
        this.stop = stop;
    }

    /** Starts the checks; started twice, only one timer runs. World thread. */
    void start() {
        if (timer == null) {
            timer = HytaleServer.SCHEDULED_EXECUTOR.scheduleAtFixedRate(
                    this::queue, CHECK_MILLIS, CHECK_MILLIS, TimeUnit.MILLISECONDS);
        }
    }

    /** Ends the checks; once is enough. */
    void cancel() {
        ScheduledFuture<?> current = timer;
        timer = null;
        if (current != null) {
            current.cancel(false);
        }
    }

    /** Queues the check on the world thread; a world that refuses it (stopped) ends the checks. Scheduler thread. */
    private void queue() {
        try {
            world.execute(check);
        } catch (RuntimeException e) {
            LOG.at(Level.FINE).withCause(e).log("HyColony: page checks end with their world");
            stopOnPlayersWorld();
            throw e; // a periodic task that throws is never run again (ScheduledExecutorService)
        }
    }

    /**
     * Runs the page's stop on the world the player is in now, where its inventory lives; nothing for a player gone, or
     * for a reference read stale off the world thread (the page, no longer shown, then stays harmless). Never throws.
     */
    private void stopOnPlayersWorld() {
        try {
            Ref<EntityStore> ref = player.getReference();
            if (ref != null && ref.isValid()) {
                ref.getStore().getExternalData().getWorld().execute(stop);
            }
        } catch (RuntimeException e) {
            LOG.at(Level.FINE).withCause(e).log("HyColony: page stop left to its player");
        }
    }
}
