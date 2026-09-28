package dev.hycolony.plugin;

import com.hypixel.hytale.logger.HytaleLogger;
import com.hypixel.hytale.server.core.universe.world.World;
import dev.hycolony.core.citizen.CitizenNames;
import dev.hycolony.core.colony.ColonyContext;
import dev.hycolony.core.colony.ColonyManager;
import dev.hycolony.core.colony.ConstructionPorts;
import dev.hycolony.core.construction.goggles.BuildGoggles;
import dev.hycolony.core.construction.wand.WandActions;
import dev.hycolony.core.crafting.recipe.CraftingSetup;
import dev.hycolony.core.kernel.WorldKey;
import dev.hycolony.core.kernel.config.ColonyConfig;
import dev.hycolony.core.kernel.event.EventBus;
import dev.hycolony.core.kernel.item.BlockKey;
import dev.hycolony.core.kernel.item.ItemKey;
import dev.hycolony.core.kernel.nav.DetouringBodies;
import dev.hycolony.core.kernel.persist.FileColonyStorage;
import dev.hycolony.core.kernel.persist.MigrationChain;
import dev.hycolony.plugin.adapter.HytaleBlocks;
import dev.hycolony.plugin.adapter.HytaleCitizenBodies;
import dev.hycolony.plugin.adapter.HytaleContainerAccess;
import dev.hycolony.plugin.adapter.HytaleGameClock;
import dev.hycolony.plugin.adapter.HytaleItemCatalog;
import dev.hycolony.plugin.adapter.HytaleNotifier;
import dev.hycolony.plugin.adapter.HytalePlayerDirectory;
import dev.hycolony.plugin.adapter.HytalePlayerInventory;
import dev.hycolony.plugin.adapter.HytalePreviewPort;
import dev.hycolony.plugin.adapter.HytaleUiPort;
import dev.hycolony.plugin.adapter.HytaleWorldBlocks;
import dev.hycolony.plugin.adapter.HytaleWorldEffects;
import dev.hycolony.plugin.adapter.HytaleWorldQuery;
import dev.hycolony.plugin.block.HutBlockSystems;
import dev.hycolony.plugin.crafting.HytaleRecipeCatalog;
import dev.hycolony.plugin.npc.CitizenSpeed;
import dev.hycolony.plugin.prefab.HytaleBlueprintSource;
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
    private final HytalePreviewPort previews;
    private final BuildGoggles goggles;
    private final WandActions wand;
    private static final HytaleLogger LOG = HytaleLogger.forEnclosingClass();

    private final long autosaveTicks;
    /** Colonies were read from disk. Never true while disabled, so a disabled runtime writes nothing. */
    private final boolean loaded;

    private boolean enabled;

    WorldRuntime(World world, RuntimeSetup setup, CitizenNames names, boolean enabled) {
        this.world = world;
        ColonyConfig config = setup.config();
        IdMap ids = setup.ids();
        this.clock = new HytaleGameClock(world);
        this.bodies = new HytaleCitizenBodies(world, ids.npcRole("npc.citizen"), new CitizenSpeed(ids.speedEffects()));
        Set<String> hutBlockIds = HutBlockSystems.byBlockId(setup).keySet(); // the builder never breaks these
        HytaleItemCatalog catalog = new HytaleItemCatalog(hutBlockIds);
        this.blocks = new HytaleBlocks(world, catalog.stacks());
        ColonyManager[] self = new ColonyManager[1];
        WandActions[] wandSelf = new WandActions[1]; // the UI port needs it before it exists
        HytaleWorldBlocks worldBlocks = new HytaleWorldBlocks(world, hutBlockIds, blocks);
        ColonyContext ctx = new ColonyContext(
                new WorldKey(world.getName()),
                config,
                clock,
                new DetouringBodies(bodies, worldBlocks, catalog), // Hytale's nav walks through fire
                new HytaleWorldQuery(world, ids.precipitationParticles()),
                new HytaleNotifier(),
                new HytaleUiPort(() -> self[0], () -> wandSelf[0], blocks, ids),
                new HytalePlayerDirectory(world),
                setup.buildings(),
                setup.jobs(),
                names,
                new Random(),
                new EventBus(),
                constructionPorts(world, setup, catalog, worldBlocks));
        this.manager = new ColonyManager(ctx);
        self[0] = manager;
        this.previews = new HytalePreviewPort(world);
        this.goggles = new BuildGoggles(manager, previews);
        this.wand =
                new WandActions(manager, previews, k -> new ItemKey(ids.itemId(k)), k -> new BlockKey(ids.blockId(k)));
        wandSelf[0] = wand;
        openStorage(manager, world, enabled);
        this.loaded = enabled;
        this.enabled = enabled;
        this.autosaveTicks = config.hycolony().autosaveIntervalMinutes() * 60L * 20L;
    }

    /** The construction adapters of {@code world}. */
    private static ConstructionPorts constructionPorts(
            World world, RuntimeSetup setup, HytaleItemCatalog catalog, HytaleWorldBlocks worldBlocks) {
        IdMap ids = setup.ids();
        return new ConstructionPorts(
                catalog,
                worldBlocks,
                new HytaleContainerAccess(world, catalog.stacks()),
                new HytalePlayerInventory(world, catalog.stacks()),
                new HytaleBlueprintSource(ids, setup.styles()),
                new HytaleWorldEffects(world, ids.fireworks()),
                // Read here, before openStorage loads the colonies: a load drops every learnt recipe it does not know.
                new CraftingSetup(HytaleRecipeCatalog.load(), setup.craftingRules()));
    }

    /** Points the colonies' persistence at the world's save folder; reads them only when {@code enabled}. */
    private static void openStorage(ColonyManager manager, World world, boolean enabled) {
        manager.persistence()
                .setStorage(new FileColonyStorage(world.getSavePath().resolve("hycolony")), MigrationChain.sp3b());
        if (enabled) {
            manager.persistence().loadAll(); // disabled (asset ids missing): leave the files alone
        }
    }

    /** One core tick (1/20 s). */
    public void tickCore() {
        if (!enabled) {
            return;
        }
        try {
            clock.advance();
            manager.tick();
            goggles.tick();
            wand.tick();
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

    public BuildGoggles goggles() {
        return goggles;
    }

    public WandActions wand() {
        return wand;
    }

    public HytalePreviewPort previews() {
        return previews;
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
