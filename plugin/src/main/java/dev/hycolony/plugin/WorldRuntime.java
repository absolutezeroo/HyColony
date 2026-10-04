package dev.hycolony.plugin;

import com.hypixel.hytale.logger.HytaleLogger;
import com.hypixel.hytale.server.core.universe.world.World;
import dev.hycolony.core.app.ColonyManager;
import dev.hycolony.core.app.api.ColonyClockState;
import dev.hycolony.core.app.goggles.BuildGoggles;
import dev.hycolony.core.app.wand.WandActions;
import dev.hycolony.core.citizen.CitizenNames;
import dev.hycolony.core.colony.ColonyContext;
import dev.hycolony.core.colony.GamePorts;
import dev.hycolony.core.kernel.WorldKey;
import dev.hycolony.core.kernel.config.ColonyConfig;
import dev.hycolony.core.kernel.event.EventBus;
import dev.hycolony.core.kernel.item.BlockKey;
import dev.hycolony.core.kernel.item.ItemKey;
import dev.hycolony.core.kernel.nav.DetouringBodies;
import dev.hycolony.core.kernel.perf.TickTimings;
import dev.hycolony.core.kernel.persist.FileColonyStorage;
import dev.hycolony.core.kernel.persist.MigrationChain;
import dev.hycolony.plugin.adapter.HytaleBlocks;
import dev.hycolony.plugin.adapter.HytaleCitizenBodies;
import dev.hycolony.plugin.adapter.HytaleGameClock;
import dev.hycolony.plugin.adapter.HytaleItemCatalog;
import dev.hycolony.plugin.adapter.HytaleNotifier;
import dev.hycolony.plugin.adapter.HytalePlayerDirectory;
import dev.hycolony.plugin.adapter.HytalePreviewPort;
import dev.hycolony.plugin.adapter.HytaleUiPort;
import dev.hycolony.plugin.adapter.HytaleWorldQuery;
import dev.hycolony.plugin.npc.GuardedBodies;
import dev.hycolony.plugin.npc.body.CitizenSpeed;
import dev.hycolony.plugin.ui.highlight.HighlightMarkers;
import java.util.Random;
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
    /** The goggles' tick, timed for HyLens's /hylens perf. */
    private final Runnable tickGoggles;
    /** The wand's tick, timed for HyLens's /hylens perf. */
    private final Runnable tickWand;
    /** The autosave of dirty colonies, timed for HyLens's /hylens perf. */
    private final Runnable saveDirty;

    private static final HytaleLogger LOG = HytaleLogger.forEnclosingClass();

    private final long autosaveTicks;
    private final ColonyClockState clockState = new ColonyClockState();
    /** Core ticks due since the last autosave, paused or not. */
    private long sinceSave;
    /** Whether the citizens were halted since the pause began or its last steps ran. */
    private boolean halted;
    /** Colonies were read from disk. Never true while disabled, so a disabled runtime writes nothing. */
    private final boolean loaded;

    private boolean enabled;

    WorldRuntime(World world, RuntimeSetup setup, CitizenNames names, boolean enabled) {
        this.world = world;
        world.getWorldMapManager().addMarkerProvider(HighlightMarkers.KEY, new HighlightMarkers());
        ColonyConfig config = setup.config();
        IdMap ids = setup.ids();
        this.clock = new HytaleGameClock(world);
        HytaleItemCatalog catalog = new HytaleItemCatalog(ids.farming().hoeLevels());
        this.bodies = new HytaleCitizenBodies(
                world, ids.npcs().role("npc.citizen"), new CitizenSpeed(ids.speedEffects()), catalog.stacks());
        this.blocks = new HytaleBlocks(world, catalog.stacks());
        ColonyManager[] self = new ColonyManager[1];
        WandActions[] wandSelf = new WandActions[1]; // the UI port needs it before it exists
        GamePorts ports = WorldPorts.create(world, setup, catalog, blocks);
        ColonyContext ctx = new ColonyContext(
                new WorldKey(world.getName()),
                config,
                clock,
                new DetouringBodies(
                        new GuardedBodies(bodies),
                        ports.blocks(),
                        ports.blockCatalog()), // Hytale's nav walks through fire
                bodies.health(),
                bodies.seats(),
                new HytaleWorldQuery(world, ids.precipitationParticles()),
                new HytaleNotifier(),
                new HytalePlayerDirectory(world),
                setup.buildings(),
                setup.jobs(),
                names,
                new Random(),
                new EventBus(),
                ports,
                new TickTimings(System::nanoTime, clock::currentTick));
        this.manager = new ColonyManager(ctx, new HytaleUiPort(() -> self[0], () -> wandSelf[0], blocks, ids));
        self[0] = manager;
        this.previews = new HytalePreviewPort(world);
        this.goggles = new BuildGoggles(manager, previews);
        this.wand =
                new WandActions(manager, previews, k -> new ItemKey(ids.itemId(k)), k -> new BlockKey(ids.blockId(k)));
        wandSelf[0] = wand;
        this.tickGoggles = ctx.timings().timed("goggles", goggles::tick);
        this.tickWand = ctx.timings().timed("wand", wand::tick);
        this.saveDirty = ctx.timings().timed("autosave", manager.persistence()::saveDirty);
        openStorage(manager, world, enabled);
        this.loaded = enabled;
        this.enabled = enabled;
        this.autosaveTicks = config.hycolony().autosaveIntervalMinutes() * 60L * 20L;
    }

    /** Points the colonies' persistence at the world's save folder; reads them only when {@code enabled}. */
    private static void openStorage(ColonyManager manager, World world, boolean enabled) {
        manager.persistence()
                .setStorage(new FileColonyStorage(world.getSavePath().resolve("hycolony")), MigrationChain.sp4());
        if (enabled) {
            manager.persistence().loadAll(); // disabled (asset ids missing): leave the files alone
        }
    }

    /**
     * Runs the {@code due} core ticks the time elapsed calls for, fewer while a debugging tool paused the colonies
     * ({@link ColonyClockState}): the citizens then stand still. Autosaves on time, paused or not.
     */
    public void runCore(int due) {
        int run = clockState.allow(due);
        for (int i = 0; i < run; i++) {
            tickCore();
        }
        haltWhilePaused(run);
        autosave(due);
    }

    /** One core tick (1/20 s). */
    private void tickCore() {
        if (!enabled) {
            return;
        }
        try {
            clock.advance();
            manager.tick();
            tickGoggles.run();
            tickWand.run();
        } catch (RuntimeException e) {
            LOG.at(Level.SEVERE).withCause(e).log("HyColony tick failed in world '%s'", world.getName());
        }
    }

    /**
     * Stops the citizens once the pause begins, and again once its steps ran out: while steps run, their walks go on
     * at their pace.
     */
    private void haltWhilePaused(int run) {
        if (!clockState.paused() || run > 0 || clockState.stepsPending()) {
            halted = false;
            return;
        }
        if (!halted) {
            bodies.haltAll();
            halted = true;
        }
    }

    /** Saves the colonies changed since, every {@code autosaveTicks} core ticks due. */
    private void autosave(int due) {
        sinceSave += due;
        if (!enabled || sinceSave < autosaveTicks) {
            return;
        }
        sinceSave = 0;
        try {
            saveDirty.run();
        } catch (RuntimeException e) {
            LOG.at(Level.SEVERE).withCause(e).log("HyColony autosave failed in world '%s'", world.getName());
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

    /** Whether and how a debugging tool paused this world's colonies. */
    public ColonyClockState clockState() {
        return clockState;
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
