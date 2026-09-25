package dev.hycolony.plugin;

import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.component.system.tick.TickingSystem;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import javax.annotation.Nonnull;

/**
 * Drives the core at exactly 20 ticks/s whatever the server tick rate. DelayedSystem is not used on
 * purpose: it resets its timer instead of carrying the remainder, which would drift.
 */
public final class ColonyTickSystem extends TickingSystem<EntityStore> {
    private static final float CORE_TICK_SECONDS = 0.05f;
    /** After a long stall, drop the backlog instead of fast-forwarding (MineColonies loses ticks too). */
    private static final int MAX_CATCH_UP = 10;

    private final WorldRuntimes runtimes;
    private final Map<String, Float> accumulators = new ConcurrentHashMap<>();

    public ColonyTickSystem(WorldRuntimes runtimes) {
        this.runtimes = runtimes;
    }

    @Override
    public void tick(float dt, int systemIndex, @Nonnull Store<EntityStore> store) {
        WorldRuntime rt = runtimes.of(store.getExternalData().getWorld());
        if (rt == null) {
            return;
        }
        String key = rt.world().getName();
        float acc = accumulators.getOrDefault(key, 0f) + dt;
        int steps = 0;
        while (acc >= CORE_TICK_SECONDS && steps < MAX_CATCH_UP) {
            acc -= CORE_TICK_SECONDS;
            rt.tickCore();
            steps++;
        }
        if (steps == MAX_CATCH_UP) {
            acc = 0f;
        }
        accumulators.put(key, acc);
    }
}
