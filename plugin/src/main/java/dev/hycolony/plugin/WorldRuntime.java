package dev.hycolony.plugin;

import com.hypixel.hytale.server.core.universe.world.World;
import dev.hycolony.core.building.BuildingTypes;
import dev.hycolony.core.citizen.CitizenNames;
import dev.hycolony.core.colony.ColonyContext;
import dev.hycolony.core.colony.ColonyManager;
import dev.hycolony.core.kernel.WorldKey;
import dev.hycolony.core.kernel.config.ColonyConfig;
import dev.hycolony.core.kernel.event.EventBus;
import dev.hycolony.core.kernel.persist.FileColonyStorage;
import dev.hycolony.core.kernel.persist.MigrationChain;
import dev.hycolony.plugin.adapter.HytaleBlocks;
import dev.hycolony.plugin.adapter.HytaleCitizenBodies;
import dev.hycolony.plugin.adapter.HytaleGameClock;
import dev.hycolony.plugin.adapter.HytaleNotifier;
import dev.hycolony.plugin.adapter.HytalePlayerDirectory;
import dev.hycolony.plugin.adapter.HytaleUiPort;
import dev.hycolony.plugin.adapter.HytaleWorldQuery;
import java.util.Random;

/** One ColonyManager and its adapters for one Hytale world. World thread only. */
public final class WorldRuntime {
    private final World world;
    private final HytaleGameClock clock;
    private final HytaleCitizenBodies bodies;
    private final HytaleBlocks blocks;
    private final ColonyManager manager;
    private final long autosaveTicks;
    private boolean enabled = true;

    WorldRuntime(World world, ColonyConfig config, IdMap ids, CitizenNames names) {
        this.world = world;
        this.clock = new HytaleGameClock(world);
        this.bodies = new HytaleCitizenBodies(world, ids.npcRole("npc.citizen"));
        this.blocks = new HytaleBlocks(world);
        ColonyManager[] self = new ColonyManager[1];
        ColonyContext ctx = new ColonyContext(new WorldKey(world.getName()), config, clock, bodies,
                new HytaleWorldQuery(world), new HytaleNotifier(),
                new HytaleUiPort(() -> self[0], blocks, ids.itemId("hut.townhall")),
                new HytalePlayerDirectory(world), BuildingTypes.defaults(), names, new Random(), new EventBus());
        this.manager = new ColonyManager(ctx);
        self[0] = manager;
        manager.setStorage(new FileColonyStorage(world.getSavePath().resolve("hycolony")), MigrationChain.sp0());
        manager.loadAll();
        this.autosaveTicks = config.autosaveIntervalMinutes() * 60L * 20L;
    }

    /** One core tick (1/20 s). */
    public void tickCore() {
        if (!enabled) {
            return;
        }
        clock.advance();
        manager.tick();
        if (clock.currentTick() % autosaveTicks == 0) {
            manager.saveDirty();
        }
    }

    public World world() { return world; }
    public ColonyManager manager() { return manager; }
    public HytaleCitizenBodies bodies() { return bodies; }
    public HytaleGameClock clock() { return clock; }
    public HytaleBlocks blocks() { return blocks; }
    public boolean enabled() { return enabled; }
    void setEnabled(boolean enabled) { this.enabled = enabled; }
}
