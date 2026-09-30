package dev.hylens.plugin.watch;

import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.component.system.tick.TickingSystem;
import com.hypixel.hytale.logger.HytaleLogger;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.entity.entities.player.hud.HudManager;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import dev.hycolony.api.ColonyWorld;
import dev.hycolony.api.debug.DebugAccess;
import dev.hylens.core.draw.WatchShapes;
import dev.hylens.core.hud.TargetCell;
import dev.hylens.core.hud.WatchHudView;
import dev.hylens.core.menu.Menus;
import dev.hylens.core.watch.Watches;
import dev.hylens.plugin.HyColonyAccess;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.logging.Level;
import javax.annotation.Nonnull;
import org.jspecify.annotations.Nullable;

/**
 * Every {@link #REFRESH_SECONDS}, on each world's thread: shows what the citizen an operator there watches thinks, and
 * draws its walk for that operator alone (spec 2026-09-30, § 6.2, § 6.3); takes the panel off an operator who stopped
 * watching. Its alerts come from {@link DebugAccess#check}, which confirms a lasting one across these regular calls.
 */
public final class WatchRefreshSystem extends TickingSystem<EntityStore> {
    /** Seconds between two refreshes: 10 ticks of the core; measured in game at no visible cost (plan, task 10). */
    static final float REFRESH_SECONDS = 0.5f;

    private static final HytaleLogger LOG = HytaleLogger.forEnclosingClass();

    private final Watches watches;
    private final Menus menus;
    /** Seconds since each world's last round, one box per world: nothing is allocated per tick. */
    private final Map<String, float[]> sinceRefresh = new ConcurrentHashMap<>();
    /** Set by any world's thread: one failure is logged SEVERE, the next ones FINE. */
    private final AtomicBoolean failedOnce = new AtomicBoolean();
    /** Set by HyLens's shutdown: a tick still running must not put back a panel {@link #takeDown} took off. */
    private volatile boolean stopped;

    public WatchRefreshSystem(Watches watches, Menus menus) {
        this.watches = watches;
        this.menus = menus;
    }

    @Override
    public void tick(float dt, int systemIndex, @Nonnull Store<EntityStore> store) {
        if (stopped) {
            return;
        }
        World world = store.getExternalData().getWorld();
        float[] elapsed = sinceRefresh.computeIfAbsent(world.getName(), k -> new float[1]);
        elapsed[0] += dt;
        if (elapsed[0] < REFRESH_SECONDS) {
            return;
        }
        elapsed[0] = 0f;
        Optional<ColonyWorld> colonies;
        try {
            colonies = HyColonyAccess.world(world);
        } catch (RuntimeException e) { // another mod's code: out of a TickingSystem, it would stop the world
            LOG.at(failedOnce.getAndSet(true) ? Level.FINE : Level.SEVERE).withCause(e).log(
                    "HyLens: HyColony's api failed");
            return;
        }
        for (PlayerRef player : world.getPlayerRefs()) {
            // Out of a TickingSystem, an exception would stop the world's thread; one operator's never stops another's.
            try {
                refresh(world, store, colonies, player);
            } catch (RuntimeException e) {
                LOG.at(failedOnce.getAndSet(true) ? Level.FINE : Level.SEVERE).withCause(e).log(
                        "HyLens: the watch refresh failed");
            }
        }
    }

    /** Stops every refresh, from HyLens's shutdown, before the panels are taken down. Any thread. */
    public void stop() {
        stopped = true;
    }

    /**
     * On {@code world}'s thread: takes the panel off every player there, as HyLens stops; its HUD stays in each
     * player's HudManager otherwise, frozen on the screen.
     */
    public static void takeDown(World world) {
        Store<EntityStore> store = world.getEntityStore().getStore();
        for (PlayerRef player : world.getPlayerRefs()) {
            @Nullable Player component = component(store, player);
            if (component != null && component.getHudManager().getCustomHud(WatchHud.KEY) != null) {
                component.getHudManager().removeCustomHud(player, WatchHud.KEY);
            }
        }
    }

    private void refresh(World world, Store<EntityStore> store, Optional<ColonyWorld> colonies, PlayerRef player) {
        @Nullable Player component = component(store, player);
        if (component == null) {
            return;
        }
        HudManager huds = component.getHudManager();
        Optional<Watched> watched =
                watches.watched(player.getUuid()).flatMap(c -> colonies.flatMap(w -> Watched.read(w, c)));
        if (watched.isEmpty()) {
            if (huds.getCustomHud(WatchHud.KEY) != null) {
                huds.removeCustomHud(player, WatchHud.KEY);
            }
            return;
        }
        WatchHud hud;
        if (huds.getCustomHud(WatchHud.KEY) instanceof WatchHud shown) {
            hud = shown;
        } else {
            hud = new WatchHud(player);
            huds.addCustomHud(player, hud);
        }
        Watched w = watched.get();
        Optional<TargetCell> cell = w.debug().walkTarget().flatMap(t -> TargetCells.at(world, t));
        hud.show(WatchHudView.lines(w.citizen(), w.debug(), w.alerts(), cell));
        ShapePackets.send(
                player,
                WatchShapes.shapes(
                        w.citizen(),
                        w.debug(),
                        w.alerts(),
                        menus.state(player.getUuid()).layers()));
    }

    private static @Nullable Player component(Store<EntityStore> store, PlayerRef player) {
        @Nullable Ref<EntityStore> ref = player.getReference();
        return ref == null || !ref.isValid() ? null : store.getComponent(ref, Player.getComponentType());
    }
}
