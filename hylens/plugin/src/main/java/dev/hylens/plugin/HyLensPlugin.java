package dev.hylens.plugin;

import com.hypixel.hytale.server.core.plugin.JavaPlugin;
import com.hypixel.hytale.server.core.plugin.JavaPluginInit;
import dev.hylens.plugin.command.HyLensCommand;
import javax.annotation.Nonnull;

/** HyLens's entry point: the /hylens commands, which reach HyColony through its api only. */
public final class HyLensPlugin extends JavaPlugin {
    public HyLensPlugin(@Nonnull JavaPluginInit init) {
        super(init);
    }

    @Override
    protected void setup() {
        getCommandRegistry().registerCommand(new HyLensCommand());
    }
}
