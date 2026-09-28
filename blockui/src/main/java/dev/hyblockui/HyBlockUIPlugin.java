package dev.hyblockui;

import com.hypixel.hytale.server.core.plugin.JavaPlugin;
import com.hypixel.hytale.server.core.plugin.JavaPluginInit;
import javax.annotation.Nonnull;

/**
 * HyBlockUI's entry point. Hytale loads every mod jar from its Main class (PendingLoadJavaPlugin); the library itself
 * registers nothing, its windows are opened by the mods that use it.
 */
public final class HyBlockUIPlugin extends JavaPlugin {
    public HyBlockUIPlugin(@Nonnull JavaPluginInit init) {
        super(init);
    }
}
