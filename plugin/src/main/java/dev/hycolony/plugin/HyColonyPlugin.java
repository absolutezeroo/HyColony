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
import dev.hycolony.plugin.block.ExplosionProtectionSystem;
import dev.hycolony.plugin.block.HutBlockSystems;
import dev.hycolony.plugin.block.ProtectionSystems;
import dev.hycolony.plugin.command.HyColonyCommand;
import dev.hycolony.plugin.config.ConfigQuarantine;
import dev.hycolony.plugin.config.HyColonyConfig;
import dev.hycolony.plugin.goggles.GogglesSystems;
import dev.hycolony.plugin.npc.BuilderSensorHyColonyTarget;
import dev.hycolony.plugin.npc.CitizenBodyLifecycleSystem;
import dev.hycolony.plugin.npc.CitizenFireImmunitySystems;
import dev.hycolony.plugin.npc.CitizenUseSystem;
import dev.hycolony.plugin.npc.HyColonyComponents;
import dev.hycolony.plugin.prefab.HytaleBlueprintSource;
import dev.hycolony.plugin.ui.wand.WandInteraction;
import java.util.List;
import java.util.UUID;
import java.util.logging.Level;
import javax.annotation.Nonnull;

public final class HyColonyPlugin extends JavaPlugin {
    private final Config<HyColonyConfig> config;
    private final IdMap ids = IdMap.loadBundled();

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
        WorldRuntimes worlds = new WorldRuntimes(RuntimeSetup.create(colonyConfig, ids));

        HyColonyComponents.register(getEntityStoreRegistry());
        NPCPlugin.get().registerCoreComponentType("HyColonyTarget", BuilderSensorHyColonyTarget::new);

        getEntityStoreRegistry().registerSystem(new ColonyTickSystem(worlds));
        getEntityStoreRegistry().registerSystem(new CitizenBodyLifecycleSystem(worlds));
        getEntityStoreRegistry().registerSystem(new HutBlockSystems.Place(worlds));
        getEntityStoreRegistry().registerSystem(new HutBlockSystems.Break(worlds));
        getEntityStoreRegistry().registerSystem(new HutBlockSystems.Use(worlds));
        getEntityStoreRegistry().registerSystem(new CitizenUseSystem(worlds));
        getEntityStoreRegistry().registerSystem(new CitizenFireImmunitySystems.Grant());
        getEntityStoreRegistry().registerSystem(new CitizenFireImmunitySystems.Guard());
        getEntityStoreRegistry().registerSystem(new ProtectionSystems.Place(worlds));
        getEntityStoreRegistry().registerSystem(new ProtectionSystems.Break(worlds));
        getEntityStoreRegistry().registerSystem(new ProtectionSystems.Use(worlds));
        getEntityStoreRegistry().registerSystem(new ExplosionProtectionSystem(worlds));
        getEntityStoreRegistry().registerSystem(new GogglesSystems.ArmorChange(worlds, ids.itemId("build_goggles")));
        getEntityStoreRegistry().registerSystem(new GogglesSystems.Visibility(worlds));
        getEventRegistry()
                .registerGlobal(
                        PlayerReadyEvent.class,
                        e -> GogglesSystems.onPlayerReady(worlds, ids.itemId("build_goggles"), e));
        WandInteraction.register(this, worlds);
        getCommandRegistry().registerCommand(new HyColonyCommand(worlds, ids, colonyConfig.commands()));

        // Assets (blocks, items, NPC roles) are all loaded once a world starts: validate ids there.
        // World.onStart dispatches this on the world thread: create the runtime inline, before any
        // chunk (and its citizen NPCs) loads, so CitizenBodyLifecycleSystem finds it.
        getEventRegistry().registerGlobal(StartWorldEvent.class, e -> {
            try {
                validateIds(worlds);
                HytaleBlueprintSource.prewarm(); // once, in the background: assets are loaded by now
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
                        .log(
                                "HyColony failed to start for world '%s'",
                                e.getWorld().getName());
            }
        });
        // LAST: another listener may still cancel the removal, and then the runtime must stay.
        getEventRegistry().registerGlobal(EventPriority.LAST, RemoveWorldEvent.class, e -> {
            if (!e.isCancelled()) { // an EXCEPTIONAL removal always reports not cancelled
                worlds.remove(e.getWorld());
            }
        });
        getEventRegistry().register(ShutdownEvent.class, e -> worlds.all().forEach(WorldRuntime::saveAll));
        getEventRegistry().register(PlayerDisconnectEvent.class, e -> {
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
        });

        getLogger().at(Level.INFO).log("HyColony setup complete");
    }

    /** Runs one disconnect clean-up; a failure is logged and does not skip the next one. */
    private void safely(String what, Runnable cleanup) {
        try {
            cleanup.run();
        } catch (RuntimeException ex) {
            getLogger().at(Level.SEVERE).withCause(ex).log("HyColony: %s clean-up on disconnect failed", what);
        }
    }

    private void validateIds(WorldRuntimes worlds) {
        List<String> errors = ids.validate();
        if (errors.isEmpty()) {
            worlds.setEnabled(true);
            return;
        }
        for (String error : errors) {
            getLogger().at(Level.SEVERE).log("HyColony: missing asset id %s", error);
        }
        getLogger()
                .at(Level.SEVERE)
                .log("HyColony disabled: vital asset ids are missing (see above). Saves are untouched.");
        worlds.setEnabled(false);
    }
}
