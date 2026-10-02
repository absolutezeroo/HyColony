package dev.hycolony.plugin.ui.wand;

import com.hypixel.hytale.component.ComponentAccessor;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.logger.HytaleLogger;
import com.hypixel.hytale.protocol.BlockPosition;
import com.hypixel.hytale.server.core.entity.InteractionContext;
import com.hypixel.hytale.server.core.entity.entities.player.pages.CustomUIPage;
import com.hypixel.hytale.server.core.modules.interaction.interaction.config.server.OpenCustomUIInteraction;
import com.hypixel.hytale.server.core.plugin.PluginBase;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import dev.hycolony.core.kernel.BlockPos;
import dev.hycolony.plugin.WorldRuntime;
import dev.hycolony.plugin.WorldRuntimes;
import java.util.Optional;
import java.util.logging.Level;
import javax.annotation.Nonnull;
import org.jspecify.annotations.Nullable;

/**
 * The build tool's use (ST ItemBuildTool.useOn/use), registered as the {@code HyColony_Build_Tool} page of the item's
 * {@code OpenCustomUI} interaction. It never returns a page itself: {@link
 * dev.hycolony.core.app.wand.WandActions#open} shows the window through the UI port, or refuses. World
 * thread (interaction tick).
 */
public final class WandInteraction implements OpenCustomUIInteraction.CustomPageSupplier {
    /** The page id in the item's {@code Interactions.*.Page.Id}. */
    private static final String PAGE_ID = "HyColony_Build_Tool";

    private static final HytaleLogger LOG = HytaleLogger.forEnclosingClass();
    private final WorldRuntimes runtimes;

    private WandInteraction(WorldRuntimes runtimes) {
        this.runtimes = runtimes;
    }

    /**
     * Registers the page id, before the item assets that name it are decoded (plugin setup), and the colony borders
     * shown while the tool is held.
     */
    public static void register(PluginBase plugin, WorldRuntimes runtimes) {
        OpenCustomUIInteraction.registerCustomPageSupplier(
                plugin, WandPage.class, PAGE_ID, new WandInteraction(runtimes));
        plugin.getEntityStoreRegistry().registerSystem(new ColonyBorderSystem(runtimes));
    }

    /** Opens the window at the clicked block, or at the kept anchor on a click in the air; always null. */
    @Override
    public @Nullable CustomUIPage tryCreate(
            @Nonnull Ref<EntityStore> ref,
            @Nonnull ComponentAccessor<EntityStore> accessor,
            @Nonnull PlayerRef player,
            @Nonnull InteractionContext context) {
        try {
            WorldRuntime rt = runtimes.of(accessor.getExternalData().getWorld());
            if (rt != null && rt.enabled()) {
                rt.wand().open(player.getUuid(), anchor(context.getTargetBlock()));
            }
        } catch (RuntimeException e) {
            LOG.at(Level.SEVERE).withCause(e).log("HyColony build tool failed to open");
        }
        return null;
    }

    /**
     * The block above the clicked one. Deviation from MC: ST anchors on the clicked face; an OpenCustomUI interaction
     * does not wait for the client's data, so the face is not known and the top face is assumed.
     */
    private static Optional<BlockPos> anchor(@Nullable BlockPosition target) {
        return target == null ? Optional.empty() : Optional.of(new BlockPos(target.x, target.y + 1, target.z));
    }
}
