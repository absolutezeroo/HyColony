package dev.hydomum.plugin.cutter;

import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import dev.hyblockui.api.Texts;
import dev.hyblockui.api.UiSounds;
import dev.hydomum.plugin.registry.OrnamentVariantRegistry;
import java.util.List;
import java.util.Optional;

/** Opens the cutter window, with the player's material slots, for a player who used a cutter block. World thread. */
final class CutterOpener {
    private CutterOpener() {}

    /** Opens the window for player, with its open sound; tells them when the ornaments are not loaded yet. */
    static void open(World world, Ref<EntityStore> player, CutterSettings settings) {
        Store<EntityStore> store = player.getStore();
        PlayerRef playerRef = store.getComponent(player, PlayerRef.getComponentType());
        Player playerComponent = store.getComponent(player, Player.getComponentType());
        if (playerRef == null || playerComponent == null) {
            return;
        }
        Optional<OrnamentVariantRegistry.Catalogs> catalogs =
                settings.registry().catalogs();
        if (catalogs.isEmpty()) {
            playerRef.sendMessage(Texts.translated("hycolony.ornament.failed", List.of("load")));
            return;
        }
        CutterPage page = new CutterPage(playerRef, new CutterPage.Setup(world, settings, catalogs.get()));
        // The slots are a real container window, so the client drags items into them natively (CutterSlots).
        playerComponent.getPageManager().openCustomPageWithWindows(player, store, page, page.slotsWindow());
        UiSounds.play(playerRef, settings.openSound()); // as a bench window (CraftingWindow)
    }
}
