package dev.hycolony.plugin;

import com.hypixel.hytale.logger.HytaleLogger;
import com.hypixel.hytale.server.core.universe.world.World;
import dev.hycolony.core.building.BuildingTypes;
import dev.hycolony.core.citizen.CitizenNames;
import dev.hycolony.core.colony.ColonyContext;
import dev.hycolony.core.colony.ColonyManager;
import dev.hycolony.core.colony.ConstructionPorts;
import dev.hycolony.core.construction.hut.ConstructionBuildingTypes;
import dev.hycolony.core.job.JobRegistry;
import dev.hycolony.core.kernel.WorldKey;
import dev.hycolony.core.kernel.config.ColonyConfig;
import dev.hycolony.core.kernel.event.EventBus;
import dev.hycolony.core.kernel.persist.FileColonyStorage;
import dev.hycolony.core.kernel.persist.MigrationChain;
import dev.hycolony.plugin.adapter.HytaleBlocks;
import dev.hycolony.plugin.adapter.HytaleBlueprintSource;
import dev.hycolony.plugin.adapter.HytaleCitizenBodies;
import dev.hycolony.plugin.adapter.HytaleContainerAccess;
import dev.hycolony.plugin.adapter.HytaleGameClock;
import dev.hycolony.plugin.adapter.HytaleItemCatalog;
import dev.hycolony.plugin.adapter.HytaleNotifier;
import dev.hycolony.plugin.adapter.HytalePlayerDirectory;
import dev.hycolony.plugin.adapter.HytalePlayerInventory;
import dev.hycolony.plugin.adapter.HytaleUiPort;
import dev.hycolony.plugin.adapter.HytaleWorldBlocks;
import dev.hycolony.plugin.adapter.HytaleWorldQuery;
import dev.hycolony.plugin.block.HutBlockSystems;
import java.util.Random;
import java.util.Set;
import java.util.logging.Level;

/** One ColonyManager and its adapters for one Hytale world. World thread only. */
public final class WorldRuntime {
    private final World world;
    private final HytaleGameClock clock;
    private final HytaleCitizenBodies bodies;
    private final HytaleBlocks blocks;
    private final ColonyManager manager;
    private static final HytaleLogger LOG = HytaleLogger.forEnclosingClass();

    private final long autosaveTicks;
    /** Colonies were read from disk. Never true while disabled, so a disabled runtime writes nothing. */
    private final boolean loaded;

    private boolean enabled;

    WorldRuntime(World world, ColonyConfig config, IdMap ids, CitizenNames names, boolean enabled) {
        this.world = world;
        this.clock = new HytaleGameClock(world);
        this.bodies = new HytaleCitizenBodies(world, ids.npcRole("npc.citizen"));
        this.blocks = new HytaleBlocks(world);
        ColonyManager[] self = new ColonyManager[1];
        JobRegistry jobs = JobRegistry.defaults();
        ConstructionBuildingTypes.register(jobs);
        Set<String> hutBlockIds = HutBlockSystems.byBlockId(ids).keySet(); // the builder never breaks these
        ColonyContext ctx = new ColonyContext(
                new WorldKey(world.getName()),
                config,
                clock,
                bodies,
                new HytaleWorldQuery(world),
                new HytaleNotifier(),
                new HytaleUiPort(() -> self[0], blocks, ids),
                new HytalePlayerDirectory(world),
                BuildingTypes.defaults(),
                jobs,
                names,
                new Random(),
                new EventBus(),
                new ConstructionPorts(
                        new HytaleItemCatalog(hutBlockIds),
                        new HytaleWorldBlocks(world, hutBlockIds),
                        new HytaleContainerAccess(world),
                        new HytalePlayerInventory(world),
                        new HytaleBlueprintSource()));
        this.manager = new ColonyManager(ctx);
        self[0] = manager;
        manager.persistence()
                .setStorage(new FileColonyStorage(world.getSavePath().resolve("hycolony")), MigrationChain.sp1());
        if (enabled) {
            manager.persistence().loadAll(); // disabled (asset ids missing): leave the files alone
        }
        this.loaded = enabled;
        this.enabled = enabled;
        this.autosaveTicks = config.autosaveIntervalMinutes() * 60L * 20L;
    }

    /** One core tick (1/20 s). */
    public void tickCore() {
        if (!enabled) {
            return;
        }
        try {
            clock.advance();
            manager.tick();
            if (clock.currentTick() % autosaveTicks == 0) {
                manager.persistence().saveDirty();
            }
        } catch (RuntimeException e) {
            LOG.at(Level.SEVERE).withCause(e).log("HyColony tick failed in world '%s'", world.getName());
        }
    }

    /** Saves every colony, on the calling thread (must be the world thread, or the world is gone). */
    public void saveAll() {
        if (enabled) {
            manager.persistence().saveAll();
        }
    }

    public World world() {
        return world;
    }

    public ColonyManager manager() {
        return manager;
    }

    public HytaleCitizenBodies bodies() {
        return bodies;
    }

    public HytaleGameClock clock() {
        return clock;
    }

    public HytaleBlocks blocks() {
        return blocks;
    }

    public boolean enabled() {
        return enabled;
    }
    /** A runtime that never loaded its colonies stays disabled: enabling it would overwrite their files. */
    void setEnabled(boolean enabled) {
        this.enabled = enabled && loaded;
    }
}
