package dev.hylens.plugin;

import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.logger.HytaleLogger;
import com.hypixel.hytale.server.core.event.events.player.PlayerDisconnectEvent;
import com.hypixel.hytale.server.core.modules.entity.component.Spectating;
import com.hypixel.hytale.server.core.modules.entity.gamemode.GameModeTypes;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.Universe;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import dev.hylens.core.check.NewAlerts;
import dev.hylens.core.menu.Menus;
import dev.hylens.core.watch.Watches;
import dev.hylens.plugin.command.MenuClock;
import dev.hylens.plugin.command.MenuCommand;
import dev.hylens.plugin.send.MapSend;
import dev.hylens.plugin.watch.WatchRefreshSystem;
import java.util.UUID;
import java.util.logging.Level;
import org.jspecify.annotations.Nullable;

/**
 * Takes an operator out of what HyLens holds for them, as they leave or HyLens stops: their watch and its spectator
 * mode (saved with the player), their pauses, menu choices, alerts told and armed map, their panel and open menu.
 */
final class OperatorExit {
    private static final HytaleLogger LOG = HytaleLogger.forEnclosingClass();

    private final Watches watches;
    private final Menus menus;
    private final MenuClock clock;
    private final NewAlerts alerts;
    private final MapSend map;

    OperatorExit(Watches watches, Menus menus, MenuClock clock, NewAlerts alerts, MapSend map) {
        this.watches = watches;
        this.menus = menus;
        this.clock = clock;
        this.alerts = alerts;
        this.map = map;
    }

    /**
     * Forgets the leaver and resumes their pauses, then, on their world's thread, forgets them again and takes them out
     * of the spectator mode: a click or a round already queued there runs first and may have remembered them. On
     * whatever thread called Universe.removePlayer: before it removes the player, at once on the world's thread, else
     * after the tasks already queued there, so the exit runs first (Universe.java:1676-1700).
     */
    void onDisconnect(PlayerDisconnectEvent e) {
        try {
            UUID operator = e.getPlayerRef().getUuid();
            forget(operator);
            resumePauses(operator);
            boolean watched = watches.stop(operator).isPresent();
            @Nullable Ref<EntityStore> ref = e.getPlayerRef().getReference();
            if (ref == null || !ref.isValid()) {
                return;
            }
            Store<EntityStore> store = ref.getStore();
            World world = store.getExternalData().getWorld();
            Runnable leave = () -> {
                forget(operator);
                leaveSpectator(operator, watched, ref, store);
                resumeFor(world, operator);
            };
            if (world.isInThread()) {
                leave.run();
            } else {
                execute(world, leave);
            }
        } catch (RuntimeException ex) {
            LOG.at(Level.SEVERE).withCause(ex).log("HyLens: stopping a watch on disconnect failed");
        }
    }

    /**
     * As HyLens stops, on {@code world}'s thread: closes the HyLens menus open there, takes the watch panel off every
     * player and each watcher out of the spectator mode. HyLens's class loader may be closed by now: a LinkageError is
     * caught too, since it would stop the world's thread.
     */
    void onStop(World world) {
        execute(world, () -> {
            try {
                MenuCommand.closeOpen(world);
            } catch (RuntimeException | LinkageError e) {
                LOG.at(Level.WARNING).withCause(e).log("HyLens: closing the menus failed");
            }
            try {
                WatchRefreshSystem.takeDown(world);
            } catch (RuntimeException | LinkageError e) {
                LOG.at(Level.WARNING).withCause(e).log("HyLens: taking the watch HUD down failed");
            }
            try {
                leaveSpectators(world);
            } catch (RuntimeException | LinkageError e) {
                LOG.at(Level.WARNING).withCause(e).log("HyLens: leaving the spectator mode failed");
            }
        });
    }

    /** On {@code world}'s thread: resumes its colonies if {@code operator} still holds their pause; logs a failure. */
    private void resumeFor(World world, UUID operator) {
        try {
            clock.resumeFor(world, operator);
        } catch (RuntimeException e) {
            LOG.at(Level.SEVERE).withCause(e).log("HyLens: resuming the colonies on disconnect failed");
        }
    }

    private void forget(UUID operator) {
        menus.forget(operator);
        alerts.forget(operator);
        map.disarm(operator);
    }

    /**
     * Resumes the colonies {@code operator} paused through HyLens, each world on its own thread, where it is decided
     * whether they still hold the pause (spec 2026-09-30, § 6.1).
     */
    private void resumePauses(UUID operator) {
        for (String name : clock.pausedBy(operator)) {
            @Nullable World world = Universe.get().getWorld(name);
            if (world != null) {
                execute(world, () -> resumeFor(world, operator));
            }
        }
    }

    /** On {@code world}'s thread: takes each of its players who watched a citizen out of the spectator mode. */
    private void leaveSpectators(World world) {
        for (PlayerRef player : world.getPlayerRefs()) {
            @Nullable Ref<EntityStore> ref = player.getReference();
            if (ref != null && ref.isValid()) {
                leaveSpectator(player.getUuid(), false, ref, ref.getStore());
            }
        }
    }

    /**
     * On the world's thread: takes {@code operator} out of the spectator mode if they watched a citizen, or started a
     * watch from a command queued before they left or HyLens stopped; stops that watch too.
     */
    private void leaveSpectator(UUID operator, boolean watched, Ref<EntityStore> ref, Store<EntityStore> store) {
        try {
            boolean startedSince = watches.stop(operator).isPresent();
            if ((watched || startedSince) && ref.isValid() && Spectating.isSpectating(ref, store)) {
                GameModeTypes.exit(ref, store);
            }
        } catch (RuntimeException ex) {
            LOG.at(Level.SEVERE).withCause(ex).log("HyLens: leaving the spectator mode failed");
        }
    }

    /** Runs {@code task} on {@code world}'s thread; a world refusing tasks is stopping, players included. */
    private static void execute(World world, Runnable task) {
        try {
            world.execute(task);
        } catch (RuntimeException e) {
            LOG.at(Level.FINE).withCause(e).log("HyLens: world %s is stopping", world.getName());
        }
    }
}
