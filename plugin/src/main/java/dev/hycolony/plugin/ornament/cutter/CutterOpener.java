package dev.hycolony.plugin.ornament.cutter;

import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import dev.hycolony.core.kernel.port.Msg;
import dev.hycolony.plugin.adapter.HytaleNotifier;
import dev.hycolony.plugin.ornament.registry.OrnamentVariantRegistry;
import java.util.Optional;

/** Opens the cutter window, with the player's material slots, for a player who used a cutter block. World thread. */
final class CutterOpener {
    private CutterOpener() {}

    /** Opens the window for player, a craft taking craftMillis; tells them when the ornaments are not loaded yet. */
    static void open(
            World world,
            Ref<EntityStore> player,
            OrnamentVariantRegistry registry,
            CutterGroupMemory memory,
            long craftMillis) {
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
        CutterPage page =
                new CutterPage(playerRef, new CutterPage.Setup(world, registry, catalogs.get(), memory, craftMillis));
        // The slots are a real container window, so the client drags items into them natively (CutterSlots).
        playerComponent.getPageManager().openCustomPageWithWindows(player, store, page, page.slotsWindow());
    }
}
