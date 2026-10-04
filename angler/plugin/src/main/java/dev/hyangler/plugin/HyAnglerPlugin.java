package dev.hyangler.plugin;

import com.hypixel.hytale.server.core.plugin.JavaPlugin;
import com.hypixel.hytale.server.core.plugin.JavaPluginInit;
import javax.annotation.Nonnull;

/** HyAngler's entry point (spec 2026-10-04-hyangler-design). */
public final class HyAnglerPlugin extends JavaPlugin {
    public HyAnglerPlugin(@Nonnull JavaPluginInit init) {
        super(init);
    }

    @Override
    protected void setup() {}
}
