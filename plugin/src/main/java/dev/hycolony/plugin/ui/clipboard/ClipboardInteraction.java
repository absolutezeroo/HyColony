package dev.hycolony.plugin.ui.clipboard;

import com.hypixel.hytale.component.ComponentAccessor;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.logger.HytaleLogger;
import com.hypixel.hytale.protocol.BlockPosition;
import com.hypixel.hytale.server.core.entity.InteractionContext;
import com.hypixel.hytale.server.core.entity.entities.player.pages.CustomUIPage;
import com.hypixel.hytale.server.core.inventory.ItemStack;
import com.hypixel.hytale.server.core.inventory.container.ItemContainer;
import com.hypixel.hytale.server.core.modules.interaction.interaction.config.server.OpenCustomUIInteraction;
import com.hypixel.hytale.server.core.plugin.PluginBase;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import dev.hycolony.core.app.clipboard.ClipboardActions;
import dev.hycolony.core.kernel.BlockPos;
import dev.hycolony.plugin.WorldRuntime;
import dev.hycolony.plugin.WorldRuntimes;
import dev.hycolony.plugin.ui.RequestsPage;
import java.util.Optional;
import java.util.logging.Level;
import javax.annotation.Nonnull;
import org.jspecify.annotations.Nullable;

/**
 * The clipboard's use (MC ItemClipboard.useOn and use), registered as the {@code HyColony_Clipboard} page of the
 * item's {@code OpenCustomUI} interaction: on a hut it notes the hut's colony in the item, anywhere else it opens the
 * noted colony's requests through the core. It never returns a page itself. World thread (interaction tick).
 */
public final class ClipboardInteraction implements OpenCustomUIInteraction.CustomPageSupplier {
    /** The page id in the item's {@code Interactions.*.Page.Id}. */
    private static final String PAGE_ID = "HyColony_Clipboard";

    private static final HytaleLogger LOG = HytaleLogger.forEnclosingClass();
    private final WorldRuntimes runtimes;

    private ClipboardInteraction(WorldRuntimes runtimes) {
        this.runtimes = runtimes;
    }

    /** Registers the page id, before the item assets that name it are decoded (plugin setup). */
    public static void register(PluginBase plugin, WorldRuntimes runtimes) {
        OpenCustomUIInteraction.registerCustomPageSupplier(
                plugin, RequestsPage.class, PAGE_ID, new ClipboardInteraction(runtimes));
    }

    /** Notes the clicked hut's colony, else opens the clipboard's window; always null. */
    @Override
    public @Nullable CustomUIPage tryCreate(
            @Nonnull Ref<EntityStore> ref,
            @Nonnull ComponentAccessor<EntityStore> accessor,
            @Nonnull PlayerRef player,
            @Nonnull InteractionContext context) {
        try {
            WorldRuntime rt = runtimes.of(accessor.getExternalData().getWorld());
            ItemContainer container = context.getHeldItemContainer();
            ItemStack stack = context.getHeldItem();
            if (rt == null || !rt.enabled() || container == null || stack == null) {
                return null;
            }
            ClipboardItem item = new ClipboardItem(container, context.getHeldItemSlot(), stack);
            ClipboardActions clipboard = new ClipboardActions(rt.manager());
            Optional<Integer> noted =
                    target(context.getTargetBlock()).flatMap(pos -> clipboard.register(player.getUuid(), pos));
            if (noted.isPresent()) {
                item.withColony(noted.get());
                return null;
            }
            ClipboardItem.used(player.getUuid(), item);
            clipboard.open(player.getUuid(), item.colony(), item.showImportant());
        } catch (RuntimeException e) {
            LOG.at(Level.SEVERE).withCause(e).log("HyColony clipboard failed");
        }
        return null;
    }

    private static Optional<BlockPos> target(@Nullable BlockPosition block) {
        return block == null ? Optional.empty() : Optional.of(new BlockPos(block.x, block.y, block.z));
    }
}
