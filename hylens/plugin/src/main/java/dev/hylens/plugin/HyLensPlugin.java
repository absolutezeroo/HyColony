package dev.hylens.plugin;

import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.logger.HytaleLogger;
import com.hypixel.hytale.server.core.event.events.player.PlayerDisconnectEvent;
import com.hypixel.hytale.server.core.modules.entity.component.Spectating;
import com.hypixel.hytale.server.core.modules.entity.gamemode.GameModeTypes;
import com.hypixel.hytale.server.core.plugin.JavaPlugin;
import com.hypixel.hytale.server.core.plugin.JavaPluginInit;
import com.hypixel.hytale.server.core.universe.Universe;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import dev.hylens.core.menu.Menus;
import dev.hylens.core.watch.Watches;
import dev.hylens.plugin.command.HyLensCommand;
import dev.hylens.plugin.watch.WatchRefreshSystem;
import java.util.UUID;
import java.util.logging.Level;
import javax.annotation.Nonnull;
import org.jspecify.annotations.Nullable;

/** HyLens's entry point: the /hylens commands, the watch HUD and drawings, reaching HyColony through its api only. */
public final class HyLensPlugin extends JavaPlugin {
    private static final HytaleLogger LOG = HytaleLogger.forEnclosingClass();

    private final Watches watches = new Watches();
    private final Menus menus = new Menus();
    private final WatchRefreshSystem refresh = new WatchRefreshSystem(watches, menus);

    public HyLensPlugin(@Nonnull JavaPluginInit init) {
        super(init);
    }

    @Override
    protected void setup() {
        getCommandRegistry().registerCommand(new HyLensCommand(this, watches, menus, HyLensIds.load()));
        getEventRegistry().register(PlayerDisconnectEvent.class, this::onDisconnect);
        getEntityStoreRegistry().registerSystem(refresh);
    }

    /**
     * Takes the watch panel off every player, each world on its own thread: nothing refreshes it once HyLens is gone.
     * A world that no longer takes tasks is stopping, and takes its players' HUDs with it.
     */
    @Override
    protected void shutdown() {
        refresh.stop();
        for (World world : Universe.get().getWorlds().values()) {
            try {
                world.execute(() -> {
                    try {
                        WatchRefreshSystem.takeDown(world);
                    } catch (RuntimeException e) {
                        LOG.at(Level.WARNING).withCause(e).log("HyLens: taking the watch HUD down failed");
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
     * On the world's thread: takes {@code operator} out of the spectator mode if they watched a citizen, or started a
     * watch from a command queued before they left; stops that watch too.
     */
    private void leaveSpectator(UUID operator, boolean watched, Ref<EntityStore> ref, Store<EntityStore> store) {
        try {
            boolean startedSince = watches.stop(operator).isPresent();
            if ((watched || startedSince) && ref.isValid() && Spectating.isSpectating(ref, store)) {
                GameModeTypes.exit(ref, store);
            }
        } catch (RuntimeException ex) {
            LOG.at(Level.SEVERE).withCause(ex).log("HyLens: leaving the spectator mode on disconnect failed");
        }
    }
}
