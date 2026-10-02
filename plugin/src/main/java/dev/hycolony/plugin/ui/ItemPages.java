package dev.hycolony.plugin.ui;

import com.hypixel.hytale.server.core.plugin.PluginBase;
import dev.hycolony.plugin.WorldRuntimes;
import dev.hycolony.plugin.ui.clipboard.ClipboardInteraction;
import dev.hycolony.plugin.ui.clipboard.ClipboardItem;
import dev.hycolony.plugin.ui.wand.WandInteraction;
import java.util.UUID;

/**
 * The pages HyColony's items open (OpenCustomUI), the build tool's and the clipboard's, and the colony borders shown
 * while the build tool is held.
 */
public final class ItemPages {
    private ItemPages() {}

    /** Registers them, before the item assets that name them are decoded (plugin setup). */
    public static void register(PluginBase plugin, WorldRuntimes runtimes) {
        WandInteraction.register(plugin, runtimes);
        ClipboardInteraction.register(plugin, runtimes);
    }

    /** Forgets the clipboard {@code player} last used and the borders drawn for them. */
    public static void disconnect(UUID player) {
        ClipboardItem.forget(player);
        WandInteraction.disconnect(player);
    }
}
