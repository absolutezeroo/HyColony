package dev.hylens.plugin;

import com.hypixel.hytale.logger.HytaleLogger;
import com.hypixel.hytale.server.core.event.events.player.PlayerDisconnectEvent;
import com.hypixel.hytale.server.core.plugin.JavaPlugin;
import com.hypixel.hytale.server.core.plugin.JavaPluginInit;
import com.hypixel.hytale.server.core.universe.Universe;
import com.hypixel.hytale.server.core.universe.world.World;
import dev.hycolony.api.ApiVersion;
import dev.hylens.core.ApiCompatibility;
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
import java.util.logging.Level;
import javax.annotation.Nonnull;

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
    private final OperatorExit exit = new OperatorExit(watches, menus, clock, alerts, map);
    /** Set once setup registered HyLens: an api refused leaves nothing to stop. */
    private volatile boolean started;

    public HyLensPlugin(@Nonnull JavaPluginInit init) {
        super(init);
    }

    /**
     * Registers HyLens, unless HyColony runs an api of another version than the one HyLens is built against: its
     * {@code @Experimental} parts may have changed, and a missing method would stop a world's thread.
     */
    @Override
    protected void setup() {
        if (!ApiCompatibility.accepts(ApiVersion.CURRENT)) {
            LOG.at(Level.SEVERE).log(
                    "HyLens is built against HyColony's api %s, which runs %s: HyLens stays off",
                    ApiCompatibility.BUILT_AGAINST, ApiVersion.CURRENT);
            return;
        }
        getCommandRegistry()
                .registerCommand(
                        new HyLensCommand(this, new LensParts(watches, menus, clock, alerts, HyLensIds.load()), map));
        getEventRegistry().register(PlayerDisconnectEvent.class, exit::onDisconnect);
        getEntityStoreRegistry().registerSystem(refresh);
        getEntityStoreRegistry().registerSystem(autoCheck);
        map.start();
        started = true;
    }

    /**
     * Stops reading the map packets and the systems, then frees every world's operators, each world on its own thread:
     * nothing refreshes the panel once HyLens is gone, and /hylens unwatch goes with it.
     */
    @Override
    protected void shutdown() {
        if (!started) {
            return;
        }
        map.stop();
        refresh.stop();
        autoCheck.stop();
        for (World world : Universe.get().getWorlds().values()) {
            exit.onStop(world);
        }
    }
}
