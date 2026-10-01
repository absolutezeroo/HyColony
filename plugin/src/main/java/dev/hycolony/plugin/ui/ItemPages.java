package dev.hycolony.plugin.ui;

import com.hypixel.hytale.server.core.plugin.PluginBase;
import dev.hycolony.plugin.WorldRuntimes;
import dev.hycolony.plugin.ui.clipboard.ClipboardInteraction;
import dev.hycolony.plugin.ui.wand.WandInteraction;

/** The pages HyColony's items open (OpenCustomUI): the build tool's and the clipboard's. */
public final class ItemPages {
    private ItemPages() {}

    /** Registers them, before the item assets that name them are decoded (plugin setup). */
    public static void register(PluginBase plugin, WorldRuntimes runtimes) {
        WandInteraction.register(plugin, runtimes);
        ClipboardInteraction.register(plugin, runtimes);
    }
}
