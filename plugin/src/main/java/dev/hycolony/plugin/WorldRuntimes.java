package dev.hycolony.plugin;

import com.hypixel.hytale.logger.HytaleLogger;
import com.hypixel.hytale.server.core.universe.world.World;
import dev.hycolony.core.citizen.CitizenNames;
import java.util.Collection;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;
import org.jspecify.annotations.Nullable;

/** Registry of per-world runtimes (map is concurrent: worlds live on different threads). */
public final class WorldRuntimes {
    private static final HytaleLogger LOG = HytaleLogger.forEnclosingClass();

    private final Map<String, WorldRuntime> byWorld = new ConcurrentHashMap<>();
    private final RuntimeSetup setup;
    private final CitizenNames names = CitizenNames.loadDefault();
    private volatile boolean enabled = true;

    public WorldRuntimes(RuntimeSetup setup) {
        this.setup = setup;
    }

    /** The config, ids and registries every world shares. */
    public RuntimeSetup setup() {
        return setup;
    }

    /** The world's runtime; null for a null world or one without a runtime (not started, or removed). */
    public @Nullable WorldRuntime of(@Nullable World world) {
        return world == null ? null : byWorld.get(world.getName());
    }

    /** Creates and returns the world's runtime, replacing any previous one. */
    public WorldRuntime create(World world) {
        WorldRuntime rt = new WorldRuntime(world, setup, names, enabled);
        byWorld.put(world.getName(), rt);
        return rt;
    }

    /**
     * Saves the world's colonies on its thread, then forgets the runtime. Called last among the
     * RemoveWorldEvent listeners, so a cancelled removal keeps the runtime.
     */
    public void remove(World world) {
        WorldRuntime rt = byWorld.get(world.getName());
        if (rt == null) {
            return;
        }
        try {
            if (world.isInThread() || !world.isAlive()) {
                rt.saveAll(); // on the world thread, or its thread is gone: nothing else touches the colonies
            } else {
                CompletableFuture.runAsync(rt::saveAll, world).join();
            }
        } catch (RuntimeException e) {
            LOG.at(Level.SEVERE).withCause(e).log("HyColony could not save world '%s' on removal", world.getName());
        }
        byWorld.remove(world.getName());
    }

    public Collection<WorldRuntime> all() {
        return byWorld.values();
    }

    /** Disabled = nothing ticks and nothing is written (vital asset id missing). */
    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
        byWorld.values().forEach(rt -> rt.setEnabled(enabled));
    }
}
