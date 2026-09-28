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
import dev.hycolony.core.kernel.config.ColonyConfig;
import dev.hycolony.plugin.block.BlockSystems;
import dev.hycolony.plugin.command.HyColonyCommand;
import dev.hycolony.plugin.config.ConfigQuarantine;
import dev.hycolony.plugin.config.HyColonyConfig;
import dev.hycolony.plugin.goggles.GogglesSystems;
import dev.hycolony.plugin.npc.BuilderSensorHyColonyTarget;
import dev.hycolony.plugin.npc.CitizenBodyLifecycleSystem;
import dev.hycolony.plugin.npc.CitizenFireImmunitySystems;
import dev.hycolony.plugin.npc.CitizenUseSystem;
import dev.hycolony.plugin.npc.HyColonyComponents;
import dev.hycolony.plugin.ornament.Ornaments;
import dev.hycolony.plugin.prefab.HytaleBlueprintSource;
import dev.hycolony.plugin.subplugin.SubPlugins;
import dev.hycolony.plugin.ui.wand.WandInteraction;
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
        ConfigQuarantine.moveAsideIfUnreadable(getDataDirectory().resolve("config.json"), HyColonyConfig.CODEC);
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
        WorldRuntimes worlds = new WorldRuntimes(RuntimeSetup.create(colonyConfig, packs));
        IdMap ids = worlds.setup().ids();

        HyColonyComponents.register(getEntityStoreRegistry());
        NPCPlugin.get().registerCoreComponentType("HyColonyTarget", BuilderSensorHyColonyTarget::new);
        registerSystems(worlds, ids);
        WandInteraction.register(this, worlds);
        getCommandRegistry().registerCommand(new HyColonyCommand(worlds, ids, colonyConfig.commands(), packs));
        registerWorldEvents(worlds);
        Ornaments.register(this);
        getEventRegistry().register(PlayerDisconnectEvent.class, e -> onDisconnect(worlds, ids, e));

        getLogger().at(Level.INFO).log("HyColony setup complete");
    }

    /** Registers the entity systems and the goggles' player-ready hook, in their original order. */
    private void registerSystems(WorldRuntimes worlds, IdMap ids) {
        getEntityStoreRegistry().registerSystem(new ColonyTickSystem(worlds));
        getEntityStoreRegistry().registerSystem(new CitizenBodyLifecycleSystem(worlds));
        BlockSystems.register(getEntityStoreRegistry(), worlds, ids);
        getEntityStoreRegistry().registerSystem(new CitizenUseSystem(worlds));
        getEntityStoreRegistry().registerSystem(new CitizenFireImmunitySystems.Grant());
        getEntityStoreRegistry().registerSystem(new CitizenFireImmunitySystems.Guard());
        getEntityStoreRegistry().registerSystem(new GogglesSystems.ArmorChange(worlds, ids.itemId("build_goggles")));
        getEntityStoreRegistry().registerSystem(new GogglesSystems.Visibility(worlds));
        getEventRegistry()
                .registerGlobal(
                        PlayerReadyEvent.class,
                        e -> GogglesSystems.onPlayerReady(worlds, ids.itemId("build_goggles"), e));
    }

    /** Creates a runtime when a world starts, drops it when the world goes, and saves every one on shutdown. */
    private void registerWorldEvents(WorldRuntimes worlds) {
        // Assets (blocks, items, NPC roles) are all loaded once a world starts: validate ids there.
        // World.onStart dispatches this on the world thread: create the runtime inline, before any
        // chunk (and its citizen NPCs) loads, so CitizenBodyLifecycleSystem finds it.
        getEventRegistry().registerGlobal(StartWorldEvent.class, e -> onWorldStart(worlds, e));
        // LAST: another listener may still cancel the removal, and then the runtime must stay.
        getEventRegistry().registerGlobal(EventPriority.LAST, RemoveWorldEvent.class, e -> {
            if (!e.isCancelled()) { // an EXCEPTIONAL removal always reports not cancelled
                worlds.remove(e.getWorld());
            }
        });
        getEventRegistry().register(ShutdownEvent.class, e -> worlds.all().forEach(WorldRuntime::saveAll));
    }

    /** Validates the ids and creates the world's runtime; a failure is logged SEVERE, never thrown. */
    private void onWorldStart(WorldRuntimes worlds, StartWorldEvent e) {
        try {
            worlds.enableIfIdsValid();
            HytaleBlueprintSource.prewarm(worlds.setup().styles()); // once, in the background: assets are loaded
            WorldRuntime created = worlds.create(e.getWorld());
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

    /** On each world's thread: takes off the leaver's goggles and wand, and cancels their unconfirmed town hall. */
    private void onDisconnect(WorldRuntimes worlds, IdMap ids, PlayerDisconnectEvent e) {
        UUID uuid = e.getPlayerRef().getUuid();
        worlds.all()
                .forEach(rt -> rt.world().execute(() -> {
                    safely("goggles", () -> rt.goggles().unequip(uuid));
                    safely("wand", () -> rt.wand().disconnect(uuid));
                }));
        worlds.all()
                .forEach(rt -> rt.world()
                        .execute(() -> safely(
                                "foundation",
                                () -> rt.manager()
                                        .foundation()
                                        .cancel(uuid)
                                        .ifPresent(pos -> rt.blocks()
                                                .removeWithDrop(
                                                        pos,
                                                        ids.blockId("hut.townhall"),
                                                        ids.itemId("hut.townhall"))))));
    }

    @Override
    protected void shutdown() {
        packs.unregisterAssets();
    }

    /** Runs one disconnect clean-up; a failure is logged and does not skip the next one. */
    private void safely(String what, Runnable cleanup) {
        try {
            cleanup.run();
        } catch (RuntimeException ex) {
            getLogger().at(Level.SEVERE).withCause(ex).log("HyColony: %s clean-up on disconnect failed", what);
        }
    }
}
