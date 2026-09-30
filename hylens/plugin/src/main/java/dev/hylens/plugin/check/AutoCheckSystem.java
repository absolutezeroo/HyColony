package dev.hylens.plugin.check;

import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.component.system.tick.TickingSystem;
import com.hypixel.hytale.logger.HytaleLogger;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import dev.hycolony.api.CitizenRef;
import dev.hycolony.api.ColonyWorld;
import dev.hycolony.api.debug.Violation;
import dev.hycolony.api.read.ColonySummary;
import dev.hylens.core.check.CheckReport;
import dev.hylens.core.check.NewAlerts;
import dev.hylens.core.menu.Menus;
import dev.hylens.plugin.HyColonyAccess;
import dev.hylens.plugin.watch.ApiMessages;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.logging.Level;
import javax.annotation.Nonnull;

/**
 * Every {@link #CHECK_SECONDS}, on each world's thread: checks its colonies for the operators there who turned the
 * automatic check on, and tells each of them the violations that newly lasted (spec 2026-09-30, § 6.5). These regular
 * checks are also what confirms a lasting state for the menu (the HUD confirms its own, at each refresh). Each colony
 * is checked once per round.
 */
public final class AutoCheckSystem extends TickingSystem<EntityStore> {
    /**
     * Seconds between two checks, in server time: 40 core ticks while the colonies run, well under HyColony's 100-tick
     * confirmation.
     */
    static final float CHECK_SECONDS = 2f;

    private static final HytaleLogger LOG = HytaleLogger.forEnclosingClass();

    private final Menus menus;
    private final NewAlerts alerts;
    /** Seconds since each world's last round, one box per world: nothing is allocated per tick. */
    private final Map<String, float[]> sinceCheck = new ConcurrentHashMap<>();
    /** Set by any world's thread: one failure is logged SEVERE, the next ones FINE. */
    private final AtomicBoolean failedOnce = new AtomicBoolean();
    /** Set by HyLens's shutdown: no check runs once HyLens stops. */
    private volatile boolean stopped;

    public AutoCheckSystem(Menus menus, NewAlerts alerts) {
        this.menus = menus;
        this.alerts = alerts;
    }

    @Override
    public void tick(float dt, int systemIndex, @Nonnull Store<EntityStore> store) {
        if (stopped) {
            return;
        }
        World world = store.getExternalData().getWorld();
        float[] elapsed = sinceCheck.computeIfAbsent(world.getName(), k -> new float[1]);
        elapsed[0] += dt;
        if (elapsed[0] < CHECK_SECONDS) {
            return;
        }
        elapsed[0] = 0f;
        List<PlayerRef> checking = world.getPlayerRefs().stream()
                .filter(p -> menus.state(p.getUuid()).autoCheck())
                .toList();
        if (checking.isEmpty()) {
            return;
        }
        // Out of a TickingSystem, an exception would stop the world's thread.
        try {
            HyColonyAccess.world(world).ifPresent(w -> check(w, checking));
        } catch (RuntimeException | LinkageError e) {
            LOG.at(failedOnce.getAndSet(true) ? Level.FINE : Level.SEVERE).withCause(e).log(
                    "HyLens: the automatic check failed");
        }
    }

    /** Stops every check, from HyLens's shutdown. Any thread. */
    public void stop() {
        stopped = true;
    }

    private void check(ColonyWorld world, List<PlayerRef> checking) {
        for (ColonySummary colony : world.colonies()) {
            List<Violation> confirmed = world.debug().check(colony.ref());
            Optional<Map<CitizenRef, String>> names = Optional.empty();
            for (PlayerRef player : checking) {
                List<Violation> fresh = alerts.fresh(player.getUuid(), colony.ref(), confirmed);
                if (fresh.isEmpty()) {
                    continue;
                }
                if (names.isEmpty()) {
                    names = Optional.of(ColonyChecks.names(world, colony.ref()));
                }
                Map<CitizenRef, String> known = names.get();
                fresh.forEach(v -> player.sendMessage(ApiMessages.of(CheckReport.fresh(colony.name(), v, known))));
            }
        }
    }
}
