package dev.hycolony.plugin;

import com.hypixel.hytale.server.core.universe.world.World;
import dev.hycolony.core.citizen.CitizenNames;
import dev.hycolony.core.kernel.config.ColonyConfig;
import java.util.Collection;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/** Registry of per-world runtimes (map is concurrent: worlds live on different threads). */
public final class WorldRuntimes {
    private final Map<String, WorldRuntime> byWorld = new ConcurrentHashMap<>();
    private final ColonyConfig config;
    private final IdMap ids;
    private final CitizenNames names = CitizenNames.loadDefault();
    private volatile boolean enabled = true;

    public WorldRuntimes(ColonyConfig config, IdMap ids) {
        this.config = config;
        this.ids = ids;
    }

    public WorldRuntime of(World world) {
        return world == null ? null : byWorld.get(world.getName());
    }

    public void create(World world) {
        WorldRuntime rt = new WorldRuntime(world, config, ids, names);
        rt.setEnabled(enabled);
        byWorld.put(world.getName(), rt);
    }

    public void remove(World world) {
        WorldRuntime rt = byWorld.remove(world.getName());
        if (rt != null) {
            rt.manager().saveAll();
        }
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
