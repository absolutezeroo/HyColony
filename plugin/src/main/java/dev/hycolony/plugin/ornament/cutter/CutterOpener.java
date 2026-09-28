package dev.hycolony.plugin.ornament.cutter;

import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.logger.HytaleLogger;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.entity.entities.player.windows.ContainerBlockWindow;
import com.hypixel.hytale.server.core.modules.block.components.ItemContainerBlock;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import dev.hycolony.core.kernel.port.Msg;
import dev.hycolony.plugin.adapter.HytaleNotifier;
import dev.hycolony.plugin.ornament.registry.OrnamentVariantRegistry;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.logging.Level;
import org.joml.Vector3i;

/**
 * Opens the cutter window on a cutter block: the page, beside a container window on the block's 2 material slots, as
 * vanilla OpenContainerInteraction opens a chest. One window per player on a block; it leaves the block's window list
 * when closed.
 */
final class CutterOpener {
    private static final HytaleLogger LOG = HytaleLogger.forEnclosingClass();
    private static final AtomicBoolean WARNED = new AtomicBoolean();

    private CutterOpener() {}

    /** Opens the window at pos for player; tells the player when the ornaments are not loaded yet. */
    static void open(
            World world,
            Ref<EntityStore> player,
            Vector3i pos,
            OrnamentVariantRegistry registry,
            CutterGroupMemory memory) {
        Store<EntityStore> store = player.getStore();
        PlayerRef playerRef = store.getComponent(player, PlayerRef.getComponentType());
        Player playerComponent = store.getComponent(player, Player.getComponentType());
        if (playerRef == null || playerComponent == null) {
            return;
        }
        Optional<OrnamentVariantRegistry.Catalogs> catalogs = registry.catalogs();
        if (catalogs.isEmpty()) {
            playerRef.sendMessage(HytaleNotifier.toMessage(Msg.of("hycolony.ornament.failed", "load")));
            return;
        }
        Optional<CutterBlock> cutter = CutterBlock.at(world, pos);
        if (cutter.isEmpty()) {
            LOG.at(WARNED.getAndSet(true) ? Level.FINE : Level.WARNING).log(
                    "hyornament: cutter at %s has no container", pos);
            return;
        }
        CutterPage page = new CutterPage(
                playerRef,
                new CutterPage.Setup(world, cutter.get().box().getItemContainer(), registry, catalogs.get(), memory));
        show(playerComponent, player, cutter.get(), page);
    }

    /**
     * Opens page beside the cutter's slot window for player, unless it is already open for them; the block's window
     * list gets no lasting entry when opening fails or throws, or this player could never open this cutter again.
     */
    private static void show(Player shown, Ref<EntityStore> player, CutterBlock cutter, CutterPage page) {
        ItemContainerBlock box = cutter.box();
        ContainerBlockWindow window = cutter.window();
        UUID uuid = page.player();
        if (box.getWindows().putIfAbsent(uuid, window) != null) {
            return;
        }
        try {
            if (!shown.getPageManager().openCustomPageWithWindows(player, player.getStore(), page, window)) {
                box.getWindows().remove(uuid, window);
                return;
            }
        } catch (RuntimeException e) {
            box.getWindows().remove(uuid, window);
            throw e;
        }
        window.registerCloseEvent(e -> {
            box.getWindows().remove(uuid, window);
            page.detach();
        });
        page.attach(window);
    }
}
