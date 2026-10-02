package dev.hycolony.plugin;

import com.hypixel.hytale.event.EventPriority;
import com.hypixel.hytale.server.core.event.events.ShutdownEvent;
import com.hypixel.hytale.server.core.event.events.player.PlayerDisconnectEvent;
import com.hypixel.hytale.server.core.event.events.player.PlayerReadyEvent;
import com.hypixel.hytale.server.core.plugin.JavaPlugin;
import com.hypixel.hytale.server.core.plugin.JavaPluginInit;
import com.hypixel.hytale.server.core.universe.world.events.RemoveWorldEvent;
import com.hypixel.hytale.server.core.universe.world.events.StartWorldEvent;
import com.hypixel.hytale.server.core.util.Config;
import com.hypixel.hytale.server.npc.NPCPlugin;
import dev.hyblockui.api.ConfigQuarantine;
import dev.hycolony.core.kernel.config.ColonyConfig;
import dev.hycolony.plugin.block.BlockSystems;
import dev.hycolony.plugin.bridge.ApiBridge;
import dev.hycolony.plugin.command.HyColonyCommand;
import dev.hycolony.plugin.config.HyColonyConfig;
import dev.hycolony.plugin.goggles.GogglesSystems;
import dev.hycolony.plugin.npc.BuilderSensorHyColonyTarget;
import dev.hycolony.plugin.npc.CitizenBodyLifecycleSystem;
import dev.hycolony.plugin.npc.HyColonyComponents;
import dev.hycolony.plugin.npc.HyColonySeek;
import dev.hycolony.plugin.npc.NpcSystems;
import dev.hycolony.plugin.prefab.HytaleBlueprintSource;
import dev.hycolony.plugin.subplugin.SubPlugins;
import dev.hycolony.plugin.ui.ItemPages;
import dev.hycolony.plugin.ui.highlight.GlowingBlock;
import java.util.UUID;
import java.util.logging.Level;
import javax.annotation.Nonnull;

public final class HyColonyPlugin extends JavaPlugin {
    private final Config<HyColonyConfig> config;
    /** Kept for {@link #shutdown}: a reload must find our asset packs unregistered. */
    private SubPlugins packs = SubPlugins.none();

    public HyColonyPlugin(@Nonnull JavaPluginInit init) {
        super(init);
        // Before withConfig: preLoad decodes the file and a malformed one would abort the whole server start.
        ConfigQuarantine.moveAsideIfUnreadable(
                "HyColony", getDataDirectory().resolve("config.json"), HyColonyConfig.CODEC);
        this.config = withConfig("config", HyColonyConfig.CODEC);
    }

    @Override
    protected void setup() {
        // save() returns a Future: log a failure instead of dropping it (CLAUDE.md sec 4).
        var _ = config.save().exceptionally(e -> {
            getLogger().at(Level.WARNING).withCause(e).log("HyColony: could not save initial config");
            return null;
        });
        ColonyConfig colonyConfig = config.get().toCore();
        // Sub-plugin asset packs must be registered here, before LoadAssetEvent (plugin-b-api § 21.1).
        packs = SubPlugins.load(this, config.get().subPlugins());
        packs.requireDomumVariants(); // before LoadAssetEvent, when HyDomum creates them
        WorldRuntimes worlds = new WorldRuntimes(RuntimeSetup.create(colonyConfig, packs));
        ApiBridge api = ApiBridge.install(worlds);
        IdMap ids = worlds.setup().ids();
        GlowingBlock.useEffect(ids.highlightEffect());

        HyColonyComponents.register(getEntityStoreRegistry());
        NPCPlugin.get().registerCoreComponentType("HyColonyTarget", BuilderSensorHyColonyTarget::new);
        NPCPlugin.get().registerCoreComponentType("HyColonySeek", HyColonySeek.Builder::new);
        registerSystems(worlds, ids);
        ItemPages.register(this, worlds);
        getCommandRegistry().registerCommand(new HyColonyCommand(worlds, ids, colonyConfig.commands(), packs));
        registerWorldEvents(worlds, api);
        getEventRegistry().register(PlayerDisconnectEvent.class, e -> onDisconnect(worlds, ids, e));

        getLogger().at(Level.INFO).log("HyColony setup complete");
    }

    /** Registers the entity systems and the goggles' player-ready hook, in their original order. */
    private void registerSystems(WorldRuntimes worlds, IdMap ids) {
        getEntityStoreRegistry().registerSystem(new ColonyTickSystem(worlds));
        getEntityStoreRegistry().registerSystem(new CitizenBodyLifecycleSystem(worlds));
        BlockSystems.register(getEntityStoreRegistry(), worlds, ids);
        NpcSystems.register(getEntityStoreRegistry(), worlds, ids);
        getEntityStoreRegistry().registerSystem(new GogglesSystems.ArmorChange(worlds, ids.itemId("build_goggles")));
        getEntityStoreRegistry().registerSystem(new GogglesSystems.Visibility(worlds));
        getEventRegistry()
                .registerGlobal(
                        PlayerReadyEvent.class,
                        e -> GogglesSystems.onPlayerReady(worlds, ids.itemId("build_goggles"), e));
    }

    /**
     * Creates a runtime when a world starts, drops it when the world goes, and saves every one on shutdown; tells
     * the api's world listeners.
     */
    private void registerWorldEvents(WorldRuntimes worlds, ApiBridge api) {
        // Assets (blocks, items, NPC roles) are all loaded once a world starts: validate ids there.
        // World.onStart dispatches this on the world thread: create the runtime inline, before any
        // chunk (and its citizen NPCs) loads, so CitizenBodyLifecycleSystem finds it.
        getEventRegistry().registerGlobal(StartWorldEvent.class, e -> onWorldStart(worlds, api, e));
        // LAST: another listener may still cancel the removal, and then the runtime must stay.
        getEventRegistry().registerGlobal(EventPriority.LAST, RemoveWorldEvent.class, e -> {
            if (!e.isCancelled()) { // an EXCEPTIONAL removal always reports not cancelled
                worlds.remove(e.getWorld());
                api.stopped(e.getWorld());
            }
        });
        getEventRegistry()
                .register(
                        ShutdownEvent.class,
                        e -> worlds.all()
                                .forEach(rt ->
                                        safely("save of world " + rt.world().getName(), rt::saveAll)));
    }

    /** Validates the ids and creates the world's runtime; a failure is logged SEVERE, never thrown. */
    private void onWorldStart(WorldRuntimes worlds, ApiBridge api, StartWorldEvent e) {
        try {
            worlds.enableIfIdsValid();
            HytaleBlueprintSource.prewarm(worlds.setup().styles()); // once, in the background: assets are loaded
            WorldRuntime created = worlds.create(e.getWorld());
            if (created.enabled()) {
                api.started(e.getWorld());
            }
            getLogger()
                    .at(Level.INFO)
                    .log(
                            "HyColony runtime ready for world '%s' (%d colonies loaded)",
                            e.getWorld().getName(), created.manager().all().size());
        } catch (RuntimeException ex) {
            getLogger()
                    .at(Level.SEVERE)
                    .withCause(ex)
                    .log("HyColony failed to start for world '%s'", e.getWorld().getName());
        }
    }

    /**
     * Forgets the leaver's last item page and drawn borders, then on each world's thread takes off their goggles and
     * wand, and cancels their unconfirmed town hall.
     */
    private void onDisconnect(WorldRuntimes worlds, IdMap ids, PlayerDisconnectEvent e) {
        UUID uuid = e.getPlayerRef().getUuid();
        safely("item pages clean-up on disconnect", () -> ItemPages.disconnect(uuid));
        // execute throws once a world stops taking tasks: guarded too, so the other worlds still clean up.
        worlds.all()
                .forEach(rt -> safely(
                        "disconnect clean-up",
                        () -> rt.world().execute(() -> {
                            safely(
                                    "goggles clean-up on disconnect",
                                    () -> rt.goggles().unequip(uuid));
                            safely(
                                    "wand clean-up on disconnect",
                                    () -> rt.wand().disconnect(uuid));
                            safely(
                                    "foundation clean-up on disconnect",
                                    () -> rt.manager()
                                            .foundation()
                                            .cancel(uuid)
                                            .ifPresent(pos -> rt.blocks()
                                                    .removeWithDrop(
                                                            pos,
                                                            ids.blockId("hut.townhall"),
                                                            ids.itemId("hut.townhall"))));
                        })));
    }

    @Override
    protected void shutdown() {
        ApiBridge.uninstall();
        packs.unregisterAssets();
    }

    /** Runs one clean-up step; a failure is logged SEVERE and does not skip the next one. */
    private void safely(String what, Runnable step) {
        try {
            step.run();
        } catch (RuntimeException ex) {
            getLogger().at(Level.SEVERE).withCause(ex).log("HyColony: %s failed", what);
        }
    }
}
