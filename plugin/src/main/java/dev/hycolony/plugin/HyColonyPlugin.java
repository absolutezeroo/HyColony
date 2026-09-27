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
import dev.hycolony.plugin.adapter.HytaleBlueprintSource;
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
import dev.hycolony.plugin.ui.wand.WandInteraction;
import java.util.List;
import java.util.UUID;
import java.util.logging.Level;
import javax.annotation.Nonnull;

public final class HyColonyPlugin extends JavaPlugin {
    private final Config<HyColonyConfig> config;
    private final IdMap ids = IdMap.loadBundled();
    private WorldRuntimes runtimes;

    public HyColonyPlugin(@Nonnull JavaPluginInit init) {
        super(init);
        // Before withConfig: preLoad decodes the file and a malformed one would abort the whole server start.
        ConfigQuarantine.moveAsideIfUnreadable(getDataDirectory().resolve("config.json"), HyColonyConfig.CODEC);
        this.config = withConfig("config", HyColonyConfig.CODEC);
    }

    @Override
    protected void setup() {
        config.save();
        ColonyConfig colonyConfig = config.get().toCore();
        runtimes = new WorldRuntimes(colonyConfig, ids);

        HyColonyComponents.register(getEntityStoreRegistry());
        NPCPlugin.get().registerCoreComponentType("HyColonyTarget", BuilderSensorHyColonyTarget::new);

        getEntityStoreRegistry().registerSystem(new ColonyTickSystem(runtimes));
        getEntityStoreRegistry().registerSystem(new CitizenBodyLifecycleSystem(runtimes));
        getEntityStoreRegistry().registerSystem(new HutBlockSystems.Place(runtimes, ids));
        getEntityStoreRegistry().registerSystem(new HutBlockSystems.Break(runtimes, ids));
        getEntityStoreRegistry().registerSystem(new HutBlockSystems.Use(runtimes, ids));
        getEntityStoreRegistry().registerSystem(new CitizenUseSystem(runtimes));
        getEntityStoreRegistry().registerSystem(new CitizenFireImmunitySystems.Grant());
        getEntityStoreRegistry().registerSystem(new CitizenFireImmunitySystems.Guard());
        getEntityStoreRegistry().registerSystem(new ProtectionSystems.Place(runtimes, ids));
        getEntityStoreRegistry().registerSystem(new ProtectionSystems.Break(runtimes, ids));
        getEntityStoreRegistry().registerSystem(new ProtectionSystems.Use(runtimes, ids));
        getEntityStoreRegistry().registerSystem(new ExplosionProtectionSystem(runtimes));
        getEntityStoreRegistry().registerSystem(new GogglesSystems.ArmorChange(runtimes, ids.itemId("build_goggles")));
        getEntityStoreRegistry().registerSystem(new GogglesSystems.Visibility(runtimes));
        getEventRegistry()
                .registerGlobal(
                        PlayerReadyEvent.class,
                        e -> GogglesSystems.onPlayerReady(runtimes, ids.itemId("build_goggles"), e));
        WandInteraction.register(this, runtimes);
        getCommandRegistry()
                .registerCommand(new HyColonyCommand(
                        runtimes, ids, colonyConfig.commands(), getIdentifier().toString(), getDataDirectory()));

        // Assets (blocks, items, NPC roles) are all loaded once a world starts: validate ids there.
        // World.onStart dispatches this on the world thread: create the runtime inline, before any
        // chunk (and its citizen NPCs) loads, so CitizenBodyLifecycleSystem finds it.
        getEventRegistry().registerGlobal(StartWorldEvent.class, e -> {
            try {
                validateIds();
                HytaleBlueprintSource.prewarm(); // once, in the background: assets are loaded by now
                runtimes.create(e.getWorld());
                getLogger()
                        .at(Level.INFO)
                        .log(
                                "HyColony runtime ready for world '%s' (%d colonies loaded)",
                                e.getWorld().getName(),
                                runtimes.of(e.getWorld()).manager().all().size());
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
                runtimes.remove(e.getWorld());
            }
        });
        getEventRegistry().register(ShutdownEvent.class, e -> runtimes.all().forEach(WorldRuntime::saveAll));
        getEventRegistry().register(PlayerDisconnectEvent.class, e -> {
            UUID uuid = e.getPlayerRef().getUuid();
            runtimes.all()
                    .forEach(rt -> rt.world().execute(() -> {
                        safely("goggles", () -> rt.goggles().unequip(uuid));
                        safely("wand", () -> rt.wand().disconnect(uuid));
                    }));
            runtimes.all()
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

    private void validateIds() {
        List<String> errors = ids.validate();
        if (errors.isEmpty()) {
            runtimes.setEnabled(true);
            return;
        }
        for (String error : errors) {
            getLogger().at(Level.SEVERE).log("HyColony: missing asset id %s", error);
        }
        getLogger()
                .at(Level.SEVERE)
                .log("HyColony disabled: vital asset ids are missing (see above). Saves are untouched.");
        runtimes.setEnabled(false);
    }

    public WorldRuntimes runtimes() {
        return runtimes;
    }
}
