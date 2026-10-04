package dev.hyangler.plugin;

import com.hypixel.hytale.server.core.modules.interaction.interaction.config.Interaction;
import com.hypixel.hytale.server.core.plugin.JavaPlugin;
import com.hypixel.hytale.server.core.plugin.JavaPluginInit;
import dev.hyangler.plugin.spike.SpikeBobberSystem;
import dev.hyangler.plugin.spike.SpikeCastInteraction;
import dev.hyangler.plugin.spike.SpikeCommand;
import javax.annotation.Nonnull;

/** HyAngler's entry point (spec 2026-10-04-hyangler-design). */
public final class HyAnglerPlugin extends JavaPlugin {
    public HyAnglerPlugin(@Nonnull JavaPluginInit init) {
        super(init);
    }

    @Override
    protected void setup() {
        // Throwaway spike (plan task 2): removed by task 14.
        getCodecRegistry(Interaction.CODEC)
                .register("HyAngler_SpikeCast", SpikeCastInteraction.class, SpikeCastInteraction.CODEC);
        SpikeBobberSystem.registerComponent(getEntityStoreRegistry());
        getEntityStoreRegistry().registerSystem(new SpikeBobberSystem());
        getCommandRegistry().registerCommand(new SpikeCommand());
    }
}
