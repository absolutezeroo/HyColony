package dev.hylens.plugin.hud;

import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.component.system.tick.TickingSystem;
import com.hypixel.hytale.logger.HytaleLogger;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.entity.entities.player.hud.HudManager;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import dev.hycolony.api.ApiText;
import dev.hycolony.api.CitizenRef;
import dev.hycolony.api.ColonyWorld;
import dev.hycolony.api.debug.CitizenDebugSnapshot;
import dev.hycolony.api.debug.DebugAccess;
import dev.hycolony.api.debug.Violation;
import dev.hycolony.api.read.CitizenSnapshot;
import dev.hycolony.plugin.api.HyColonyApi;
import dev.hylens.core.hud.TargetCell;
import dev.hylens.core.hud.WatchHudView;
import dev.hylens.core.watch.Watches;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.logging.Level;
import javax.annotation.Nonnull;
import org.jspecify.annotations.Nullable;

/**
 * Every {@link #REFRESH_SECONDS}, on each world's thread: shows what the citizen an operator there watches thinks, and
 * takes the panel off an operator who stopped watching (spec 2026-09-30, § 6.2). Its alerts come from
 * {@link DebugAccess#check}, which confirms a lasting one across these regular calls.
 */
public final class WatchHudSystem extends TickingSystem<EntityStore> {
    /** Seconds between two refreshes: 10 ticks of the core; measured in game at no visible cost (plan, task 10). */
    static final float REFRESH_SECONDS = 0.5f;

    private static final HytaleLogger LOG = HytaleLogger.forEnclosingClass();

    private final Watches watches;
    private final Map<String, Float> sinceRefresh = new ConcurrentHashMap<>();
    /** Set by any world's thread: one failure is logged SEVERE, the next ones FINE. */
    private final AtomicBoolean failedOnce = new AtomicBoolean();
    /** Set by HyLens's shutdown: a tick still running must not put back a panel {@link #takeDown} took off. */
    private volatile boolean stopped;

    public WatchHudSystem(Watches watches) {
        this.watches = watches;
    }

    @Override
    public void tick(float dt, int systemIndex, @Nonnull Store<EntityStore> store) {
        if (stopped) {
            return;
        }
        World world = store.getExternalData().getWorld();
        float elapsed = sinceRefresh.getOrDefault(world.getName(), 0f) + dt;
        if (elapsed < REFRESH_SECONDS) {
            sinceRefresh.put(world.getName(), elapsed);
            return;
        }
        sinceRefresh.put(world.getName(), 0f);
        Optional<ColonyWorld> colonies;
        try {
            colonies = HyColonyApi.get().world(world);
        } catch (RuntimeException e) { // HyColony stopped: its api holder is empty
            colonies = Optional.empty();
        }
        for (PlayerRef player : world.getPlayerRefs()) {
            // Out of a TickingSystem, an exception would stop the world's thread; one operator's never stops another's.
            try {
                refresh(world, store, colonies, player);
            } catch (RuntimeException e) {
                LOG.at(failedOnce.getAndSet(true) ? Level.FINE : Level.SEVERE).withCause(e).log(
                        "HyLens: the watch HUD failed");
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
        Optional<List<ApiText>> lines =
                watches.watched(player.getUuid()).flatMap(c -> colonies.flatMap(w -> lines(world, w, c)));
        if (lines.isEmpty()) {
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
        hud.show(lines.get());
    }

    private static @Nullable Player component(Store<EntityStore> store, PlayerRef player) {
        @Nullable Ref<EntityStore> ref = player.getReference();
        return ref == null || !ref.isValid() ? null : store.getComponent(ref, Player.getComponentType());
    }

    /**
     * The lines of {@code citizen}, its alerts and the blocks at its walk target included; empty when HyColony no
     * longer knows it.
     */
    private static Optional<List<ApiText>> lines(World world, ColonyWorld colonies, CitizenRef citizen) {
        DebugAccess debug = colonies.debug();
        Optional<CitizenDebugSnapshot> snapshot = debug.inspect(citizen);
        Optional<CitizenSnapshot> known = colonies.citizen(citizen);
        if (snapshot.isEmpty() || known.isEmpty()) {
            return Optional.empty();
        }
        List<Violation> alerts = debug.check(citizen.colony()).stream()
                .filter(v -> v.citizen().equals(Optional.of(citizen)))
                .toList();
        Optional<TargetCell> cell = snapshot.get().walkTarget().flatMap(t -> TargetCells.at(world, t));
        return Optional.of(WatchHudView.lines(known.get(), snapshot.get(), alerts, cell));
    }
}
