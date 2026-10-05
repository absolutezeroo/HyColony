package dev.hyangler.plugin;

import com.hypixel.hytale.server.core.modules.interaction.interaction.config.Interaction;
import com.hypixel.hytale.server.core.plugin.JavaPlugin;
import com.hypixel.hytale.server.core.plugin.JavaPluginInit;
import com.hypixel.hytale.server.core.util.Config;
import dev.hyangler.core.FishingService;
import dev.hyangler.plugin.bridge.ApiBridge;
import dev.hyangler.plugin.cast.CastInteraction;
import dev.hyangler.plugin.cast.CastParts;
import dev.hyangler.plugin.config.HyAnglerConfig;
import dev.hyangler.plugin.data.AnglerData;
import dev.hyangler.plugin.data.CatchAsset;
import dev.hyangler.plugin.data.FishAsset;
import dev.hyangler.plugin.data.RodAsset;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.logging.Level;
import javax.annotation.Nonnull;
import org.jspecify.annotations.Nullable;

/**
 * HyAngler's entry point (spec 2026-10-04-hyangler-design): reads its config, builds the fishing service, registers
 * the open Fish, Catches and Rods asset types (read into the core once, at start: AnglerData), installs the api for
 * other mods (ApiBridge) from setup to shutdown, and the player's fishing with a rod (CastParts, HyAngler_Cast).
 */
public final class HyAnglerPlugin extends JavaPlugin {
    private final Config<HyAnglerConfig> config;
    private @Nullable AnglerData data;

    public HyAnglerPlugin(@Nonnull JavaPluginInit init) {
        super(init);
        // Before withConfig: preLoad decodes the file and a malformed one would abort the whole server start.
        HyAnglerConfig.quarantine(getDataDirectory().resolve("config.json"));
        this.config = withConfig("config", HyAnglerConfig.CODEC);
    }

    @Override
    protected void setup() {
        var _ = config.save().exceptionally(e -> {
            getLogger().at(Level.WARNING).withCause(e).log("HyAngler: could not save initial config");
            return null;
        });
        // Another mod's hook, listener or condition may fail on every catch: the first failure of any of them in
        // WARNING, every later one in FINE, whichever mod it comes from (CLAUDE.md § 4).
        AtomicBoolean reported = new AtomicBoolean();
        FishingService fishing = new FishingService(
                config.get().toCore(),
                e -> getLogger()
                        .at(reported.getAndSet(true) ? Level.FINE : Level.WARNING)
                        .withCause(e)
                        .log("HyAngler: another mod's catch hook, listener or condition failed"));
        FishAsset.register(getAssetRegistry());
        CatchAsset.register(getAssetRegistry());
        RodAsset.register(getAssetRegistry());
        data = new AnglerData(fishing);
        AnglerIds ids = AnglerIds.load();
        ApiBridge.install(fishing, ids);
        getCodecRegistry(Interaction.CODEC).register("HyAngler_Cast", CastInteraction.class, CastInteraction.CODEC);
        CastParts.install(getEntityStoreRegistry(), fishing, ids, fishing.settings());
    }

    /** Reads the data files: after the assets (LoadAssetEvent) and every plugin's setup (HytaleServer.java:334-387). */
    @Override
    protected void start() {
        if (data != null) {
            data.ensureLoaded();
        }
    }

    @Override
    protected void shutdown() {
        ApiBridge.uninstall();
    }
}
