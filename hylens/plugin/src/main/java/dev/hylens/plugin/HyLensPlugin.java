package dev.hylens.plugin;

import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.logger.HytaleLogger;
import com.hypixel.hytale.server.core.event.events.player.PlayerDisconnectEvent;
import com.hypixel.hytale.server.core.modules.entity.component.Spectating;
import com.hypixel.hytale.server.core.modules.entity.gamemode.GameModeTypes;
import com.hypixel.hytale.server.core.plugin.JavaPlugin;
import com.hypixel.hytale.server.core.plugin.JavaPluginInit;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.Universe;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import dev.hylens.core.check.NewAlerts;
import dev.hylens.core.menu.Menus;
import dev.hylens.core.menu.Pauses;
import dev.hylens.core.watch.Watches;
import dev.hylens.plugin.check.AutoCheckSystem;
import dev.hylens.plugin.command.HyLensCommand;
import dev.hylens.plugin.command.LensParts;
import dev.hylens.plugin.command.MenuClock;
import dev.hylens.plugin.send.MapSend;
import dev.hylens.plugin.watch.WatchRefreshSystem;
import java.util.UUID;
import java.util.logging.Level;
import javax.annotation.Nonnull;
import org.jspecify.annotations.Nullable;

/**
 * HyLens's entry point: the /hylens commands, the watch HUD and drawings, the automatic check and the map's "send
 * here", reaching HyColony through its api only.
 */
public final class HyLensPlugin extends JavaPlugin {
    private static final HytaleLogger LOG = HytaleLogger.forEnclosingClass();

    private final Watches watches = new Watches();
    private final Menus menus = new Menus();
    private final MenuClock clock = new MenuClock(this, new Pauses());
    private final NewAlerts alerts = new NewAlerts();
    private final WatchRefreshSystem refresh = new WatchRefreshSystem(watches, menus);
    private final AutoCheckSystem autoCheck = new AutoCheckSystem(menus, alerts);
    private final MapSend map = new MapSend(watches, menus);

    public HyLensPlugin(@Nonnull JavaPluginInit init) {
        super(init);
    }

    @Override
    protected void setup() {
        getCommandRegistry()
                .registerCommand(
                        new HyLensCommand(this, new LensParts(watches, menus, clock, alerts, HyLensIds.load()), map));
        getEventRegistry().register(PlayerDisconnectEvent.class, this::onDisconnect);
        getEntityStoreRegistry().registerSystem(refresh);
        getEntityStoreRegistry().registerSystem(autoCheck);
        map.start();
    }

    /**
     * Stops reading the map packets, then takes the watch panel off every player and its watchers out of the spectator
     * mode, each world on its own thread: nothing refreshes the panel once HyLens is gone, and /hylens unwatch goes
     * with it. A world that no longer takes tasks is stopping, and takes its players with it.
     */
    @Override
    protected void shutdown() {
        map.stop();
        refresh.stop();
        autoCheck.stop();
        for (World world : Universe.get().getWorlds().values()) {
            try {
                world.execute(() -> {
                    // HyLens's class loader may be closed by now: a LinkageError would stop the world's thread.
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
            } catch (RuntimeException e) {
                LOG.at(Level.FINE).withCause(e).log("HyLens: world %s is stopping", world.getName());
            }
        }
    }

    /**
     * Stops the leaver's watch, then takes a watcher out of the spectator mode, which is saved with the player. On
     * whatever thread called Universe.removePlayer: before it removes the player, at once on the world's thread, else
     * after the tasks already queued there, so the exit runs first (Universe.java:1676-1700).
     */
    private void onDisconnect(PlayerDisconnectEvent e) {
        try {
            UUID operator = e.getPlayerRef().getUuid();
            menus.forget(operator);
            alerts.forget(operator);
            map.disarm(operator);
            resumePauses(operator);
            boolean watched = watches.stop(operator).isPresent();
            @Nullable Ref<EntityStore> ref = e.getPlayerRef().getReference();
            if (ref == null || !ref.isValid()) {
                return;
            }
            Store<EntityStore> store = ref.getStore();
            World world = store.getExternalData().getWorld();
            Runnable leave = () -> leaveSpectator(operator, watched, ref, store);
            if (world.isInThread()) {
                leave.run();
            } else {
                world.execute(leave);
            }
        } catch (RuntimeException ex) {
            LOG.at(Level.SEVERE).withCause(ex).log("HyLens: stopping a watch on disconnect failed");
        }
    }

    /**
     * Resumes the colonies {@code operator} paused through HyLens, each world on its own thread, where it is decided
     * whether they still hold the pause (spec 2026-09-30, § 6.1). A world that no longer takes tasks is stopping.
     */
    private void resumePauses(UUID operator) {
        for (String name : clock.pausedBy(operator)) {
            @Nullable World world = Universe.get().getWorld(name);
            if (world == null) {
                continue;
            }
            try {
                world.execute(() -> clock.resumeFor(world, operator));
            } catch (RuntimeException e) {
                LOG.at(Level.FINE).withCause(e).log("HyLens: world %s is stopping", name);
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
}
